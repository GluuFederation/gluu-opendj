/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.time.TimeService;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;
import static org.forgerock.opendj.ldap.DecodeException.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

/**
 * Implementations of time-based matching rules.
 */
final class TimeBasedMatchingRulesImpl {

    private static final TimeZone TIME_ZONE_UTC = TimeZone.getTimeZone("UTC");

    /** Constants for generating keys. */
    private static final char SECOND = 's';
    private static final char MINUTE = 'm';
    private static final char HOUR = 'h';
    private static final char MONTH = 'M';
    private static final char DATE = 'D';
    private static final char YEAR = 'Y';

    private TimeBasedMatchingRulesImpl() {
        // not instantiable
    }

    /**
     * Creates a relative time greater than matching rule.
     *
     * @return the matching rule implementation
     */
    static MatchingRuleImpl relativeTimeGTOMatchingRule() {
        return new RelativeTimeGreaterThanOrderingMatchingRuleImpl();
    }

    /**
     * Creates a relative time less than matching rule.
     *
     * @return the matching rule implementation
     */
    static MatchingRuleImpl relativeTimeLTOMatchingRule() {
        return new RelativeTimeLessThanOrderingMatchingRuleImpl();
    }

    /**
     * Creates a partial date and time matching rule.
     *
     * @return the matching rule implementation
     */
    static MatchingRuleImpl partialDateAndTimeMatchingRule() {
        return new PartialDateAndTimeMatchingRuleImpl();
    }

    /**
     * This class defines a matching rule which is used for time-based searches.
     */
    private static abstract class TimeBasedMatchingRuleImpl extends AbstractMatchingRuleImpl {

        /** Unit tests can inject fake timestamps if necessary. */
        final TimeService timeService = TimeService.SYSTEM;

        /** {@inheritDoc} */
        @Override
        public final ByteString normalizeAttributeValue(Schema schema, ByteSequence value) throws DecodeException {
            try {
                return ByteString.valueOf(GeneralizedTime.valueOf(value.toString()).getTimeInMillis());
            } catch (final LocalizedIllegalArgumentException e) {
                throw error(e.getMessageObject());
            }
        }

        /** Utility method to convert the provided integer and the provided byte representing a digit to an integer. */
        int multiplyByTenAndAddUnits(int number, byte digitByte) {
            return number * 10 + (digitByte - '0');
        }
    }

    /**
     * Defines the relative time ordering matching rule.
     */
    private static abstract class RelativeTimeOrderingMatchingRuleImpl extends TimeBasedMatchingRuleImpl {

        final Indexer indexer = new DefaultIndexer(EMR_GENERALIZED_TIME_NAME);

        /** {@inheritDoc} */
        @Override
        public Collection<? extends Indexer> createIndexers(IndexingOptions options) {
            return Collections.singletonList(indexer);
        }

        /**
         * Normalize the provided assertion value.
         * <p>
         * An assertion value may contain one of the following:
         * <pre>
         * s = second
         * m = minute
         * h = hour
         * d = day
         * w = week
         * </pre>
         *
         * An example assertion is
         * <pre>
         *   OID:=(-)1d
         * </pre>
         *
         * where a '-' means that the user intends to search only the expired
         * events. In this example we are searching for an event expired 1 day
         * back.
         * <p>
         * This method takes the assertion value adds/substracts it to/from the
         * current time and calculates a time which will be used as a relative
         * time by inherited rules.
         */
        ByteString normalizeAssertionValue(ByteSequence assertionValue) throws DecodeException {
            int index = 0;
            boolean signed = false;
            byte firstByte = assertionValue.byteAt(0);

            if (firstByte == '-') {
                // Turn the sign on to go back in past.
                signed = true;
                index = 1;
            } else if (firstByte == '+') {
                // '+" is not required but we won't reject it either.
                index = 1;
            }

            long second = 0;
            long minute = 0;
            long hour = 0;
            long day = 0;
            long week = 0;

            boolean containsTimeUnit = false;
            int number = 0;

            for (; index < assertionValue.length(); index++) {
                byte b = assertionValue.byteAt(index);
                if (isDigit((char) b)) {
                    number = multiplyByTenAndAddUnits(number, b);
                } else {
                    if (containsTimeUnit) {
                        // We already have time unit found by now.
                        throw error(WARN_ATTR_CONFLICTING_ASSERTION_FORMAT.get(assertionValue));
                    }
                    switch (b) {
                    case 's':
                        second = number;
                        break;
                    case 'm':
                        minute = number;
                        break;
                    case 'h':
                        hour = number;
                        break;
                    case 'd':
                        day = number;
                        break;
                    case 'w':
                        week = number;
                        break;
                    default:
                        throw error(WARN_ATTR_INVALID_RELATIVE_TIME_ASSERTION_FORMAT.get(assertionValue, (char) b));
                    }
                    containsTimeUnit = true;
                    number = 0;
                }
            }

            if (!containsTimeUnit) {
                // There was no time unit so assume it is seconds.
                second = number;
            }

            long delta = (second + minute * 60 + hour * 3600 + day * 24 * 3600 + week * 7 * 24 * 3600) * 1000;
            long now = timeService.now();
            return ByteString.valueOf(signed ? now - delta : now + delta);
        }

    }

    /**
     * Defines the "greater-than" relative time matching rule.
     */
    private static final class RelativeTimeGreaterThanOrderingMatchingRuleImpl extends
        RelativeTimeOrderingMatchingRuleImpl {

        /** {@inheritDoc} */
        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
            final ByteString assertionValue = normalizeAssertionValue(value);

            return new Assertion() {
                @Override
                public ConditionResult matches(ByteSequence attributeValue) {
                    return ConditionResult.valueOf(attributeValue.compareTo(assertionValue) > 0);
                }

                @Override
                public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                    return factory.createRangeMatchQuery(indexer.getIndexID(), assertionValue, ByteString.empty(),
                        false, false);
                }
            };
        }
    }

    /**
     * Defines the "less-than" relative time matching rule.
     */
    private static final class RelativeTimeLessThanOrderingMatchingRuleImpl extends
        RelativeTimeOrderingMatchingRuleImpl {

        /** {@inheritDoc} */
        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
            final ByteString assertionValue = normalizeAssertionValue(value);

            return new Assertion() {
                @Override
                public ConditionResult matches(ByteSequence attributeValue) {
                    return ConditionResult.valueOf(attributeValue.compareTo(assertionValue) < 0);
                }

                @Override
                public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                    return factory.createRangeMatchQuery(indexer.getIndexID(), ByteString.empty(), assertionValue,
                        false, false);
                }
            };
        }
    }

    /**
     * Defines the partial date and time matching rule.
     */
    private static final class PartialDateAndTimeMatchingRuleImpl extends TimeBasedMatchingRuleImpl {

        private final Indexer indexer = new PartialDateAndTimeIndexer(this);

        /** {@inheritDoc} */
        @Override
        public Collection<? extends Indexer> createIndexers(IndexingOptions options) {
            return Collections.singletonList(indexer);
        }

        /** {@inheritDoc} */
        @Override
        public Assertion getAssertion(final Schema schema, final ByteSequence value) throws DecodeException {
            final ByteString assertionValue = normalizeAssertionValue(value);

            return new Assertion() {
                @Override
                public ConditionResult matches(ByteSequence attributeValue) {
                    return valuesMatch(attributeValue, assertionValue);
                }

                @Override
                public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                    final ByteBuffer buffer = ByteBuffer.wrap(assertionValue.toByteArray());
                    int assertSecond = buffer.getInt(0);
                    int assertMinute = buffer.getInt(4);
                    int assertHour = buffer.getInt(8);
                    int assertDate = buffer.getInt(12);
                    int assertMonth = buffer.getInt(16);
                    int assertYear = buffer.getInt(20);

                    List<T> queries = new ArrayList<>();
                    if (assertSecond >= 0) {
                        queries.add(createExactMatchQuery(factory, assertSecond, SECOND));
                    }
                    if (assertMinute >= 0) {
                        queries.add(createExactMatchQuery(factory, assertMinute, MINUTE));
                    }
                    if (assertHour >= 0) {
                        queries.add(createExactMatchQuery(factory, assertHour, HOUR));
                    }
                    if (assertDate > 0) {
                        queries.add(createExactMatchQuery(factory, assertDate, DATE));
                    }
                    if (assertMonth >= 0) {
                        queries.add(createExactMatchQuery(factory, assertMonth, MONTH));
                    }
                    if (assertYear > 0) {
                        queries.add(createExactMatchQuery(factory, assertYear, YEAR));
                    }
                    return factory.createIntersectionQuery(queries);
                }

                private <T> T createExactMatchQuery(IndexQueryFactory<T> factory, int assertionValue, char type) {
                    return factory.createExactMatchQuery(indexer.getIndexID(), getKey(assertionValue, type));
                }
            };
        }

        /**
         * Normalize the provided assertion value.
         * <p>
         * An assertion value may contain one or all of the following:
         * <pre>
         * D = day
         * M = month
         * Y = year
         * h = hour
         * m = month
         * s = second
         * </pre>
         *
         * An example assertion is
         * <pre>
         *  OID:=04M
         * </pre>
         * In this example we are searching for entries corresponding to month
         * of april.
         * <p>
         * The normalized value is actually the format of : smhDMY.
         */
        private ByteString normalizeAssertionValue(ByteSequence assertionValue) throws DecodeException {
            final int initDate = 0;
            final int initValue = -1;
            int second = initValue;
            int minute = initValue;
            int hour = initValue;
            int date = initDate;
            int month = initValue;
            int year = initDate;
            int number = 0;

            int length = assertionValue.length();
            for (int index = 0; index < length; index++) {
                byte b = assertionValue.byteAt(index);
                if (isDigit((char) b)) {
                    number = multiplyByTenAndAddUnits(number, b);
                } else {
                    switch (b) {
                    case 's':
                        if (second != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_SECOND_ASSERTION_FORMAT.get(assertionValue, date));
                        }
                        second = number;
                        break;
                    case 'm':
                        if (minute != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_MINUTE_ASSERTION_FORMAT.get(assertionValue, date));
                        }
                        minute = number;
                        break;
                    case 'h':
                        if (hour != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_HOUR_ASSERTION_FORMAT.get(assertionValue, date));
                        }
                        hour = number;
                        break;
                    case 'D':
                        if (number == 0) {
                            throw error(WARN_ATTR_INVALID_DATE_ASSERTION_FORMAT.get(assertionValue, number));
                        } else if (date != initDate) {
                            throw error(WARN_ATTR_DUPLICATE_DATE_ASSERTION_FORMAT.get(assertionValue, date));
                        }
                        date = number;
                        break;
                    case 'M':
                        if (number == 0) {
                            throw error(WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.get(assertionValue, number));
                        } else if (month != initValue) {
                            throw error(WARN_ATTR_DUPLICATE_MONTH_ASSERTION_FORMAT.get(assertionValue, month));
                        }
                        month = number;
                        break;
                    case 'Y':
                        if (number == 0) {
                            throw error(WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.get(assertionValue, number));
                        } else if (year != initDate) {
                            throw error(WARN_ATTR_DUPLICATE_YEAR_ASSERTION_FORMAT.get(assertionValue, year));
                        }
                        year = number;
                        break;
                    default:
                        throw error(WARN_ATTR_INVALID_PARTIAL_TIME_ASSERTION_FORMAT.get(assertionValue, (char) b));
                    }
                    number = 0;
                }
            }

            month = toCalendarMonth(month, assertionValue);

            // Validate year, month , date , hour, minute and second in that order.
            // -1 values are allowed when these values have not been provided
            if (year < 0) {
                // A future date is allowed.
                throw error(WARN_ATTR_INVALID_YEAR_ASSERTION_FORMAT.get(assertionValue, year));
            }
            if (isDateInvalid(date, month, year)) {
                throw error(WARN_ATTR_INVALID_DATE_ASSERTION_FORMAT.get(assertionValue, date));
            }
            if (hour < initValue || hour > 23) {
                throw error(WARN_ATTR_INVALID_HOUR_ASSERTION_FORMAT.get(assertionValue, hour));
            }
            if (minute < initValue || minute > 59) {
                throw error(WARN_ATTR_INVALID_MINUTE_ASSERTION_FORMAT.get(assertionValue, minute));
            }
            // Consider leap seconds.
            if (second < initValue || second > 60) {
                throw error(WARN_ATTR_INVALID_SECOND_ASSERTION_FORMAT.get(assertionValue, second));
            }

            // Since we reached here we have a valid assertion value.
            // Construct a normalized value in the order: SECOND MINUTE HOUR DATE MONTH YEAR.
            return new ByteStringBuilder(6 * 4)
                .append(second).append(minute).append(hour)
                .append(date).append(month).append(year).toByteString();
        }

        private boolean isDateInvalid(int date, int month, int year) {
            switch (date) {
            case 29:
                return month == Calendar.FEBRUARY && !isLeapYear(year);
            case 30:
                return month == Calendar.FEBRUARY;
            case 31:
                return month != -1
                    && month != Calendar.JANUARY
                    && month != Calendar.MARCH
                    && month != Calendar.MAY
                    && month != Calendar.JULY
                    && month != Calendar.AUGUST
                    && month != Calendar.OCTOBER
                    && month != Calendar.DECEMBER;
            default:
                return date < 0 || date > 31;
            }
        }

        private boolean isLeapYear(int year) {
            return year % 400 == 0 || (year % 100 != 0 && year % 4 == 0);
        }

        private int toCalendarMonth(int month, ByteSequence value) throws DecodeException {
            if (month == -1) {
                // just allow this.
                return -1;
            } else if (1 <= month && month <= 12) {
                // java.util.Calendar months are numbered from 0
                return month - 1;
            }
            throw error(WARN_ATTR_INVALID_MONTH_ASSERTION_FORMAT.get(value, month));
        }

        private ConditionResult valuesMatch(ByteSequence attributeValue, ByteSequence assertionValue) {
            // Build the information from the attribute value.
            GregorianCalendar cal = new GregorianCalendar(TIME_ZONE_UTC);
            cal.setLenient(false);
            cal.setTimeInMillis(((ByteString) attributeValue).toLong());
            int second = cal.get(Calendar.SECOND);
            int minute = cal.get(Calendar.MINUTE);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int date = cal.get(Calendar.DATE);
            int month = cal.get(Calendar.MONTH);
            int year = cal.get(Calendar.YEAR);

            // Build the information from the assertion value.
            ByteBuffer b = ByteBuffer.wrap(assertionValue.toByteArray());
            int assertSecond = b.getInt(0);
            int assertMinute = b.getInt(4);
            int assertHour = b.getInt(8);
            int assertDate = b.getInt(12);
            int assertMonth = b.getInt(16);
            int assertYear = b.getInt(20);

            // All the non-zero and non -1 values should match.
            if ((assertSecond != -1 && assertSecond != second)
                || (assertMinute != -1 && assertMinute != minute)
                || (assertHour != -1 && assertHour != hour)
                || (assertDate != 0 && assertDate != date)
                || (assertMonth != -1 && assertMonth != month)
                || (assertYear != 0 && assertYear != year)) {
                return ConditionResult.FALSE;
            }
            return ConditionResult.TRUE;
        }

        /**
         * Decomposes an attribute value into a set of partial date and time
         * index keys.
         *
         * @param attValue
         *            The normalized attribute value
         * @param set
         *            A set into which the keys will be inserted.
         */
        private void timeKeys(ByteSequence attributeValue, Collection<ByteString> keys) {
            long timeInMillis = 0L;
            try {
                timeInMillis = GeneralizedTime.valueOf(attributeValue.toString()).getTimeInMillis();
            } catch (IllegalArgumentException e) {
                return;
            }
            GregorianCalendar cal = new GregorianCalendar(TIME_ZONE_UTC);
            cal.setTimeInMillis(timeInMillis);
            addKeyIfNotZero(keys, cal, Calendar.SECOND, SECOND);
            addKeyIfNotZero(keys, cal, Calendar.MINUTE, MINUTE);
            addKeyIfNotZero(keys, cal, Calendar.HOUR_OF_DAY, HOUR);
            addKeyIfNotZero(keys, cal, Calendar.DATE, DATE);
            addKeyIfNotZero(keys, cal, Calendar.MONTH, MONTH);
            addKeyIfNotZero(keys, cal, Calendar.YEAR, YEAR);
        }

        private void addKeyIfNotZero(Collection<ByteString> keys, GregorianCalendar cal, int calField, char type) {
            int value = cal.get(calField);
            if (value >= 0) {
                keys.add(getKey(value, type));
            }
        }

        private ByteString getKey(int value, char type) {
            return new ByteStringBuilder().append(type).append(value).toByteString();
        }
    }

    /**
     * Indexer for Partial Date and Time Matching rules.
     */
    private static final class PartialDateAndTimeIndexer implements Indexer {

        private final PartialDateAndTimeMatchingRuleImpl matchingRule;

        private PartialDateAndTimeIndexer(PartialDateAndTimeMatchingRuleImpl matchingRule) {
            this.matchingRule = matchingRule;
        }

        /** {@inheritDoc} */
        @Override
        public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) {
            matchingRule.timeKeys(value, keys);
        }

        /** {@inheritDoc} */
        @Override
        public String getIndexID() {
            return MR_PARTIAL_DATE_AND_TIME_NAME;
        }
    }
}
