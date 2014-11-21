/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.config;
import org.opends.messages.Message;



import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanParameterInfo;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.loggers.ErrorLogger;
import static org.opends.messages.ConfigMessages.*;
/**
 * This class defines an integer configuration attribute, which can hold zero or
 * more integer values.  For scalability, the actual values will be stored as
 * <CODE>long</CODE> elements, although it will be possible to interact with
 * them as integers in cases where that scalability is not required.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class IntegerConfigAttribute
       extends ConfigAttribute
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The set of active values for this attribute.
  private List<Long> activeValues;

  // The set of pending values for this attribute.
  private List<Long> pendingValues;

  // Indicates whether this attribute will impose a lower bound for its values.
  private boolean hasLowerBound;

  // Indicates whether this attribute will impose an upper bound for its values.
  private boolean hasUpperBound;

  // The lower bound for values of this attribute.
  private long lowerBound;

  // The upper bound for values of this attribute.
  private long upperBound;



  /**
   * Creates a new integer configuration attribute stub with the provided
   * information but no values.  The values will be set using the
   * <CODE>setInitialValue</CODE> method.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  isRequired           Indicates whether this configuration attribute
   *                              is required to have at least one value.
   * @param  isMultiValued        Indicates whether this configuration attribute
   *                              may have multiple values.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  hasLowerBound        Indicates whether a lower bound will be
   *                              enforced for values of this attribute.
   * @param  lowerBound           The lower bound that will be enforced for
   *                              values of this attribute.
   * @param  hasUpperBound        Indicates whether an upper bound will be
   *                              enforced for values of this attribute.
   * @param  upperBound           The upper bound that will be enforced for
   *                              values of this attribute.
   */
  public IntegerConfigAttribute(String name, Message description,
                                boolean isRequired, boolean isMultiValued,
                                boolean requiresAdminAction,
                                boolean hasLowerBound, long lowerBound,
                                boolean hasUpperBound, long upperBound)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction);


    this.hasLowerBound = hasLowerBound;
    this.lowerBound    = lowerBound;
    this.hasUpperBound = hasUpperBound;
    this.upperBound    = upperBound;

    activeValues  = new ArrayList<Long>();
    pendingValues = activeValues;
  }



  /**
   * Creates a new integer configuration attribute with the provided
   * information.  No validation will be performed on the provided value.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  isRequired           Indicates whether this configuration attribute
   *                              is required to have at least one value.
   * @param  isMultiValued        Indicates whether this configuration attribute
   *                              may have multiple values.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  hasLowerBound        Indicates whether a lower bound will be
   *                              enforced for values of this attribute.
   * @param  lowerBound           The lower bound that will be enforced for
   *                              values of this attribute.
   * @param  hasUpperBound        Indicates whether an upper bound will be
   *                              enforced for values of this attribute.
   * @param  upperBound           The upper bound that will be enforced for
   *                              values of this attribute.
   * @param  value                The value for this integer configuration
   *                              attribute.
   */
  public IntegerConfigAttribute(String name, Message description,
                                boolean isRequired, boolean isMultiValued,
                                boolean requiresAdminAction,
                                boolean hasLowerBound, long lowerBound,
                                boolean hasUpperBound, long upperBound,
                                long value)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction,
          getValueSet(value));


    this.hasLowerBound = hasLowerBound;
    this.lowerBound    = lowerBound;
    this.hasUpperBound = hasUpperBound;
    this.upperBound    = upperBound;

    activeValues = new ArrayList<Long>(1);
    activeValues.add(value);

    pendingValues = activeValues;
  }



  /**
   * Creates a new integer configuration attribute with the provided
   * information.  No validation will be performed on the provided values.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  isRequired           Indicates whether this configuration attribute
   *                              is required to have at least one value.
   * @param  isMultiValued        Indicates whether this configuration attribute
   *                              may have multiple values.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  hasLowerBound        Indicates whether a lower bound will be
   *                              enforced for values of this attribute.
   * @param  lowerBound           The lower bound that will be enforced for
   *                              values of this attribute.
   * @param  hasUpperBound        Indicates whether an upper bound will be
   *                              enforced for values of this attribute.
   * @param  upperBound           The upper bound that will be enforced for
   *                              values of this attribute.
   * @param  values               The set of values for this configuration
   *                              attribute.
   */
  public IntegerConfigAttribute(String name, Message description,
                                boolean isRequired, boolean isMultiValued,
                                boolean requiresAdminAction,
                                boolean hasLowerBound, long lowerBound,
                                boolean hasUpperBound, long upperBound,
                                List<Long> values)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction,
          getValueSet(values));


    this.hasLowerBound = hasLowerBound;
    this.lowerBound    = lowerBound;
    this.hasUpperBound = hasUpperBound;
    this.upperBound    = upperBound;

    if (values == null)
    {
      activeValues  = new ArrayList<Long>();
      pendingValues = activeValues;
    }
    else
    {
      activeValues  = values;
      pendingValues = activeValues;
    }
  }



  /**
   * Creates a new integer configuration attribute with the provided
   * information.  No validation will be performed on the provided values.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  isRequired           Indicates whether this configuration attribute
   *                              is required to have at least one value.
   * @param  isMultiValued        Indicates whether this configuration attribute
   *                              may have multiple values.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  hasLowerBound        Indicates whether a lower bound will be
   *                              enforced for values of this attribute.
   * @param  lowerBound           The lower bound that will be enforced for
   *                              values of this attribute.
   * @param  hasUpperBound        Indicates whether an upper bound will be
   *                              enforced for values of this attribute.
   * @param  upperBound           The upper bound that will be enforced for
   *                              values of this attribute.
   * @param  activeValues         The set of active values for this
   *                              configuration attribute.
   * @param  pendingValues        The set of pending values for this
   *                              configuration attribute.
   */
  public IntegerConfigAttribute(String name, Message description,
                                boolean isRequired, boolean isMultiValued,
                                boolean requiresAdminAction,
                                boolean hasLowerBound, long lowerBound,
                                boolean hasUpperBound, long upperBound,
                                List<Long> activeValues,
                                List<Long> pendingValues)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction,
          getValueSet(activeValues), (pendingValues != null),
          getValueSet(pendingValues));


    this.hasLowerBound = hasLowerBound;
    this.lowerBound    = lowerBound;
    this.hasUpperBound = hasUpperBound;
    this.upperBound    = upperBound;

    if (activeValues == null)
    {
      this.activeValues = new ArrayList<Long>();
    }
    else
    {
      this.activeValues = activeValues;
    }

    if (pendingValues == null)
    {
      this.pendingValues = this.activeValues;
    }
    else
    {
      this.pendingValues = pendingValues;
    }
  }



  /**
   * Retrieves the name of the data type for this configuration attribute.  This
   * is for informational purposes (e.g., inclusion in method signatures and
   * other kinds of descriptions) and does not necessarily need to map to an
   * actual Java type.
   *
   * @return  The name of the data type for this configuration attribute.
   */
  public String getDataType()
  {
    return "Integer";
  }



  /**
   * Retrieves the attribute syntax for this configuration attribute.
   *
   * @return  The attribute syntax for this configuration attribute.
   */
  public AttributeSyntax<?> getSyntax()
  {
    return DirectoryServer.getDefaultIntegerSyntax();
  }



  /**
   * Retrieves the active value for this configuration attribute as a long.
   * This is only valid for single-valued attributes that have a value.
   *
   * @return  The active value for this configuration attribute as a long.
   *
   * @throws  ConfigException  If this attribute does not have exactly one
   *                           active value.
   */
  public long activeValue()
         throws ConfigException
  {
    if ((activeValues == null) || activeValues.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_NO_INT_VALUE.get(getName());
      throw new ConfigException(message);
    }

    if (activeValues.size() > 1)
    {
      Message message = ERR_CONFIG_ATTR_MULTIPLE_INT_VALUES.get(getName());
      throw new ConfigException(message);
    }

    return activeValues.get(0);
  }



  /**
   * Retrieves the active value for this configuration attribute as an integer.
   * This is only valid for single-valued attributes that have a value within
   * the integer range.
   *
   * @return  The active value for this configuration attribute as an integer.
   *
   * @throws  ConfigException  If the active value of this attribute cannot be
   *                           retrieved as an integer, including if there are
   *                           no values, if there are multiple values, or if
   *                           the value is not in the range of an integer.
   */
  public int activeIntValue()
         throws ConfigException
  {
    if ((activeValues == null) || activeValues.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_NO_INT_VALUE.get(getName());
      throw new ConfigException(message);
    }

    if (activeValues.size() > 1)
    {
      Message message = ERR_CONFIG_ATTR_MULTIPLE_INT_VALUES.get(getName());
      throw new ConfigException(message);
    }

    long longValue = activeValues.get(0);
    int  intValue  = (int) longValue;
    if (intValue == longValue)
    {
      return intValue;
    }
    else
    {
      Message message = ERR_CONFIG_ATTR_VALUE_OUT_OF_INT_RANGE.get(getName());
      throw new ConfigException(message);
    }
  }



  /**
   * Retrieves the set of active values for this configuration attribute.
   *
   * @return  The set of active values for this configuration attribute.
   */
  public List<Long> activeValues()
  {
    return activeValues;
  }



  /**
   * Retrieves the pending value for this configuration attribute as a long.
   * This is only valid for single-valued attributes that have a value.  If this
   * attribute does not have any pending values, then the active value will be
   * returned.
   *
   * @return  The pending value for this configuration attribute as a long.
   *
   * @throws  ConfigException  If this attribute does not have exactly one
   *                           pending value.
   */
  public long pendingValue()
         throws ConfigException
  {
    if (! hasPendingValues())
    {
      return activeValue();
    }

    if ((pendingValues == null) || pendingValues.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_NO_INT_VALUE.get(getName());
      throw new ConfigException(message);
    }

    if (pendingValues.size() > 1)
    {
      Message message = ERR_CONFIG_ATTR_MULTIPLE_INT_VALUES.get(getName());
      throw new ConfigException(message);
    }

    return pendingValues.get(0);
  }



  /**
   * Retrieves the pending value for this configuration attribute as an integer.
   * This is only valid for single-valued attributes that have a value within
   * the integer range.  If this attribute does not have any pending values,
   * then t he active value will be returned.
   *
   * @return  The pending value for this configuration attribute as an integer.
   *
   * @throws  ConfigException  If the pending value of this attribute cannot be
   *                           retrieved as an integer, including if there are
   *                           no values, if there are multiple values, or if
   *                           the value is not in the range of an integer.
   */
  public int pendingIntValue()
         throws ConfigException
  {
    if (! hasPendingValues())
    {
      return activeIntValue();
    }

    if ((pendingValues == null) || pendingValues.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_NO_INT_VALUE.get(getName());
      throw new ConfigException(message);
    }

    if (pendingValues.size() > 1)
    {
      Message message = ERR_CONFIG_ATTR_MULTIPLE_INT_VALUES.get(getName());
      throw new ConfigException(message);
    }

    long longValue = pendingValues.get(0);
    int  intValue  = (int) longValue;
    if (intValue == longValue)
    {
      return intValue;
    }
    else
    {
      Message message = ERR_CONFIG_ATTR_VALUE_OUT_OF_INT_RANGE.get(getName());
      throw new ConfigException(message);
    }
  }



  /**
   * Retrieves the set of pending values for this configuration attribute.  If
   * there are no pending values, then the set of active values will be
   * returned.
   *
   * @return  The set of pending values for this configuration attribute.
   */
  public List<Long> pendingValues()
  {
    if (! hasPendingValues())
    {
      return activeValues;
    }

    return pendingValues;
  }



  /**
   * Indicates whether a lower bound will be enforced for the value of this
   * configuration attribute.
   *
   * @return  <CODE>true</CODE> if a lower bound will be enforced for the
   *          value of this configuration attribute, or <CODE>false</CODE> if
   *          not.
   */
  public boolean hasLowerBound()
  {
    return hasLowerBound;
  }



  /**
   * Retrieves the lower bound for the value of this configuration attribute.
   *
   * @return  The lower bound for the value of this configuration attribute.
   */
  public long getLowerBound()
  {
    return lowerBound;
  }



  /**
   * Indicates whether an upper bound will be enforced for the calculated value
   * of this configuration attribute.
   *
   * @return  <CODE>true</CODE> if an upper bound will be enforced for the
   *          calculated value of this configuration attribute, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasUpperBound()
  {
    return hasUpperBound;
  }



  /**
   * Retrieves the upper bound for the calculated value of this configuration
   * attribute.
   *
   * @return  The upper bound for the calculated value of this configuration
   *          attribute.
   */
  public long getUpperBound()
  {
    return upperBound;
  }



  /**
   * Sets the value for this integer configuration attribute.
   *
   * @param  value  The value for this integer configuration attribute.
   *
   * @throws  ConfigException  If the provided value is not acceptable.
   */
  public void setValue(long value)
         throws ConfigException
  {
    if (hasLowerBound && (value < lowerBound))
    {
      Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
          getName(), value, lowerBound);
      throw new ConfigException(message);
    }

    if (hasUpperBound && (value > upperBound))
    {
      Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
          getName(), value, upperBound);
      throw new ConfigException(message);
    }

    if (requiresAdminAction())
    {
      pendingValues = new ArrayList<Long>(1);
      pendingValues.add(value);
      setPendingValues(getValueSet(value));
    }
    else
    {
      activeValues.clear();
      activeValues.add(value);
      pendingValues = activeValues;
      setActiveValues(getValueSet(value));
    }
  }



  /**
   * Sets the values for this integer configuration attribute.
   *
   * @param  values  The set of values for this integer configuration attribute.
   *
   * @throws  ConfigException  If the provided value set or any of the
   *                           individual values are not acceptable.
   */
  public void setValues(List<Long> values)
         throws ConfigException
  {
    // First check if the set is empty and if that is allowed.
    if ((values == null) || (values.isEmpty()))
    {
      if (isRequired())
      {
        Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(getName());
        throw new ConfigException(message);
      }
      else
      {
        if (requiresAdminAction())
        {
          setPendingValues(new LinkedHashSet<AttributeValue>(0));
          pendingValues = new ArrayList<Long>();
        }
        else
        {
          setActiveValues(new LinkedHashSet<AttributeValue>(0));
          activeValues.clear();
        }
      }
    }


    // Next check if the set contains multiple values and if that is allowed.
    int numValues = values.size();
    if ((! isMultiValued()) && (numValues > 1))
    {
      Message message =
          ERR_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED.get(getName());
      throw new ConfigException(message);
    }


    // Iterate through all the provided values, make sure that they are
    // acceptable, and build the value set.
    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(numValues);
    for (long value : values)
    {
      if (hasLowerBound && (value < lowerBound))
      {
        Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
            getName(), value, lowerBound);
        throw new ConfigException(message);
      }

      if (hasUpperBound && (value > upperBound))
      {
        Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
            getName(), value, upperBound);
        throw new ConfigException(message);
      }

      String valueString = String.valueOf(value);
      AttributeValue attrValue =
          AttributeValues.create(ByteString.valueOf(valueString),
              ByteString.valueOf(valueString));

      if (valueSet.contains(attrValue))
      {
        Message message = ERR_CONFIG_ATTR_ADD_VALUES_ALREADY_EXISTS.get(
            getName(), valueString);
        throw new ConfigException(message);
      }

      valueSet.add(attrValue);
    }


    // Apply this value set to the new active or pending value set.
    if (requiresAdminAction())
    {
      pendingValues = values;
      setPendingValues(valueSet);
    }
    else
    {
      activeValues  = values;
      pendingValues = activeValues;
      setActiveValues(valueSet);
    }
  }



  /**
   * Creates the appropriate value set with the provided value.
   *
   * @param  value  The value to use to create the value set.
   *
   * @return  The constructed value set.
   */
  private static LinkedHashSet<AttributeValue> getValueSet(long value)
  {
    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(1);

    String valueString = String.valueOf(value);
    valueSet.add(AttributeValues.create(ByteString.valueOf(valueString),
        ByteString.valueOf(valueString)));

    return valueSet;
  }



  /**
   * Creates the appropriate value set with the provided values.
   *
   * @param  values  The values to use to create the value set.
   *
   * @return  The constructed value set.
   */
  private static LinkedHashSet<AttributeValue> getValueSet(List<Long> values)
  {
    if (values == null)
    {
      return null;
    }

    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(values.size());

    for (long value : values)
    {
      String valueString = String.valueOf(value);
      valueSet.add(AttributeValues.create(ByteString.valueOf(valueString),
          ByteString.valueOf(valueString)));
    }

    return valueSet;
  }



  /**
   * Applies the set of pending values, making them the active values for this
   * configuration attribute.  This will not take any action if there are no
   * pending values.
   */
  public void applyPendingValues()
  {
    if (! hasPendingValues())
    {
      return;
    }

    super.applyPendingValues();
    activeValues = pendingValues;
  }



  /**
   * Indicates whether the provided value is acceptable for use in this
   * attribute.  If it is not acceptable, then the reason should be written into
   * the provided buffer.
   *
   * @param  value         The value for which to make the determination.
   * @param  rejectReason  A buffer into which a human-readable reason for the
   *                       reject may be written.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use in
   *          this attribute, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(AttributeValue value,
                                   StringBuilder rejectReason)
  {
    // First, make sure we can represent it as a long.
    String stringValue = value.getValue().toString();
    long longValue;
    try
    {
      longValue = Long.parseLong(stringValue);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      rejectReason.append(ERR_CONFIG_ATTR_INVALID_INT_VALUE.get(
              getName(), stringValue, String.valueOf(e)));
      return false;
    }


    // Perform any necessary bounds checking.
    if (hasLowerBound && (longValue < lowerBound))
    {
      rejectReason.append(ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
              getName(), longValue, lowerBound));
      return false;
    }

    if (hasUpperBound && (longValue > upperBound))
    {
      rejectReason.append(ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
              getName(), longValue, upperBound));
      return false;
    }


    // If we've gotten here, then the value must be acceptable.
    return true;
  }



  /**
   * Converts the provided set of strings to a corresponding set of attribute
   * values.
   *
   * @param  valueStrings   The set of strings to be converted into attribute
   *                        values.
   * @param  allowFailures  Indicates whether the decoding process should allow
   *                        any failures in which one or more values could be
   *                        decoded but at least one could not.  If this is
   *                        <CODE>true</CODE> and such a condition is acceptable
   *                        for the underlying attribute type, then the returned
   *                        set of values should simply not include those
   *                        undecodable values.
   *
   * @return  The set of attribute values converted from the provided strings.
   *
   * @throws  ConfigException  If an unrecoverable problem occurs while
   *                           performing the conversion.
   */
  public LinkedHashSet<AttributeValue>
              stringsToValues(List<String> valueStrings,
                              boolean allowFailures)
         throws ConfigException
  {
    if ((valueStrings == null) || valueStrings.isEmpty())
    {
      if (isRequired())
      {
        Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(getName());
        throw new ConfigException(message);
      }
      else
      {
        return new LinkedHashSet<AttributeValue>();
      }
    }


    int numValues = valueStrings.size();
    if ((! isMultiValued()) && (numValues > 1))
    {
      Message message =
          ERR_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED.get(getName());
      throw new ConfigException(message);
    }


    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(numValues);
    for (String valueString : valueStrings)
    {
      long longValue;
      try
      {
        longValue = Long.parseLong(valueString);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_CONFIG_ATTR_INT_COULD_NOT_PARSE.get(
                valueString, getName(),
                String.valueOf(e));

        if (allowFailures)
        {
          ErrorLogger.logError(message);
          continue;
        }
        else
        {
          throw new ConfigException(message);
        }
      }


      if (hasLowerBound && (longValue < lowerBound))
      {

        Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
                getName(), longValue, lowerBound);
        if (allowFailures)
        {
          ErrorLogger.logError(message);
          continue;
        }
        else
        {
          throw new ConfigException(message);
        }
      }


      if (hasUpperBound && (longValue > upperBound))
      {
        Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
                getName(), longValue, upperBound);

        if (allowFailures)
        {
          ErrorLogger.logError(message);
          continue;
        }
        else
        {
          throw new ConfigException(message);
        }
      }


      valueSet.add(AttributeValues.create(ByteString.valueOf(valueString),
          ByteString.valueOf(valueString)));
    }


    // If this method was configured to continue on error, then it is possible
    // that we ended up with an empty list.  Check to see if this is a required
    // attribute and if so deal with it accordingly.
    if ((isRequired()) && valueSet.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(getName());
      throw new ConfigException(message);
    }


    return valueSet;
  }



  /**
   * Converts the set of active values for this configuration attribute into a
   * set of strings that may be stored in the configuration or represented over
   * protocol.  The string representation used by this method should be
   * compatible with the decoding used by the <CODE>stringsToValues</CODE>
   * method.
   *
   * @return  The string representations of the set of active values for this
   *          configuration attribute.
   */
  public List<String> activeValuesToStrings()
  {
    ArrayList<String> valueStrings =
         new ArrayList<String>(activeValues.size());
    for (long l : activeValues)
    {
      valueStrings.add(String.valueOf(l));
    }

    return valueStrings;
  }



  /**
   * Converts the set of pending values for this configuration attribute into a
   * set of strings that may be stored in the configuration or represented over
   * protocol.  The string representation used by this method should be
   * compatible with the decoding used by the <CODE>stringsToValues</CODE>
   * method.
   *
   * @return  The string representations of the set of pending values for this
   *          configuration attribute, or <CODE>null</CODE> if there are no
   *          pending values.
   */
  public List<String> pendingValuesToStrings()
  {
    if (hasPendingValues())
    {
      ArrayList<String> valueStrings =
           new ArrayList<String>(pendingValues.size());
      for (long l : pendingValues)
      {
        valueStrings.add(String.valueOf(l));
      }

      return valueStrings;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves a new configuration attribute of this type that will contain the
   * values from the provided attribute.
   *
   * @param  attributeList  The list of attributes to use to create the config
   *                        attribute.  The list must contain either one or two
   *                        elements, with both attributes having the same base
   *                        name and the only option allowed is ";pending" and
   *                        only if this attribute is one that requires admin
   *                        action before a change may take effect.
   *
   * @return  The generated configuration attribute.
   *
   * @throws  ConfigException  If the provided attribute cannot be treated as a
   *                           configuration attribute of this type (e.g., if
   *                           one or more of the values of the provided
   *                           attribute are not suitable for an attribute of
   *                           this type, or if this configuration attribute is
   *                           single-valued and the provided attribute has
   *                           multiple values).
   */
  public ConfigAttribute getConfigAttribute(List<Attribute> attributeList)
         throws ConfigException
  {
    ArrayList<Long> activeValues  = null;
    ArrayList<Long> pendingValues = null;

    for (Attribute a : attributeList)
    {
      if (a.hasOptions())
      {
        // This must be the pending value.
        if (a.hasOption(OPTION_PENDING_VALUES))
        {
          if (pendingValues != null)
          {
            // We cannot have multiple pending value sets.
            Message message =
                ERR_CONFIG_ATTR_MULTIPLE_PENDING_VALUE_SETS.get(a.getName());
            throw new ConfigException(message);
          }


          if (a.isEmpty())
          {
            if (isRequired())
            {
              // This is illegal -- it must have a value.
              Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(a.getName());
              throw new ConfigException(message);
            }
            else
            {
              // This is fine.  The pending value set can be empty.
              pendingValues = new ArrayList<Long>(0);
            }
          }
          else
          {
            int numValues = a.size();
            if ((numValues > 1) && (! isMultiValued()))
            {
              // This is illegal -- the attribute is single-valued.
              Message message =
                  ERR_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED.get(a.getName());
              throw new ConfigException(message);
            }

            pendingValues = new ArrayList<Long>(numValues);
            for (AttributeValue v : a)
            {
              long longValue;
              try
              {
                longValue = Long.parseLong(v.getValue().toString());
              }
              catch (Exception e)
              {
                Message message = ERR_CONFIG_ATTR_INT_COULD_NOT_PARSE.get(
                    v.getValue().toString(), a.getName(), String.valueOf(e));
                throw new ConfigException(message, e);
              }


              // Check the bounds set for this attribute.
              if (hasLowerBound && (longValue < lowerBound))
              {
                Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
                    a.getName(), longValue, lowerBound);
                throw new ConfigException(message);
              }

              if (hasUpperBound && (longValue > upperBound))
              {
                Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
                    a.getName(), longValue, upperBound);
                throw new ConfigException(message);
              }

              pendingValues.add(longValue);
            }
          }
        }
        else
        {
          // This is illegal -- only the pending option is allowed for
          // configuration attributes.
          Message message =
              ERR_CONFIG_ATTR_OPTIONS_NOT_ALLOWED.get(
                      a.getName());
          throw new ConfigException(message);
        }
      }
      else
      {
        // This must be the active value.
        if (activeValues!= null)
        {
          // We cannot have multiple active value sets.
          Message message =
              ERR_CONFIG_ATTR_MULTIPLE_ACTIVE_VALUE_SETS.get(a.getName());
          throw new ConfigException(message);
        }


        if (a.isEmpty())
        {
          if (isRequired())
          {
            // This is illegal -- it must have a value.
            Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(a.getName());
            throw new ConfigException(message);
          }
          else
          {
            // This is fine.  The active value set can be empty.
            activeValues = new ArrayList<Long>(0);
          }
        }
        else
        {
          int numValues = a.size();
          if ((numValues > 1) && (! isMultiValued()))
          {
            // This is illegal -- the attribute is single-valued.
            Message message =
                ERR_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED.get(a.getName());
            throw new ConfigException(message);
          }

          activeValues = new ArrayList<Long>(numValues);
          for (AttributeValue v : a)
          {
            long longValue;
            try
            {
              longValue = Long.parseLong(v.getValue().toString());
            }
            catch (Exception e)
            {
              Message message = ERR_CONFIG_ATTR_INT_COULD_NOT_PARSE.get(
                  v.getValue().toString(), a.getName(), String.valueOf(e));
              throw new ConfigException(message, e);
            }


            // Check the bounds set for this attribute.
            if (hasLowerBound && (longValue < lowerBound))
            {
              Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
                  a.getName(), longValue, lowerBound);
              throw new ConfigException(message);
            }

            if (hasUpperBound && (longValue > upperBound))
            {
              Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
                  a.getName(), longValue, upperBound);
              throw new ConfigException(message);
            }

            activeValues.add(longValue);
          }
        }
      }
    }

    if (activeValues == null)
    {
      // This is not OK.  The value set must contain an active value.
      Message message = ERR_CONFIG_ATTR_NO_ACTIVE_VALUE_SET.get(getName());
      throw new ConfigException(message);
    }

    if (pendingValues == null)
    {
      // This is OK.  We'll just use the active value set.
      pendingValues = activeValues;
    }


    return new IntegerConfigAttribute(getName(), getDescription(), isRequired(),
                                      isMultiValued(), requiresAdminAction(),
                                      hasLowerBound, lowerBound, hasUpperBound,
                                      upperBound, activeValues, pendingValues);
  }



  /**
   * Retrieves a JMX attribute containing the value set for this
   * configuration attribute (active or pending).
   *
   * @param pending indicates if pending or active  values are required.
   *
   * @return  A JMX attribute containing the active value set for this
   *          configuration attribute, or <CODE>null</CODE> if it does not have
   *          any active values.
   */
  private javax.management.Attribute _toJMXAttribute(boolean pending)
  {
    List<Long> requestedValues ;
    String name ;
    if (pending)
    {
        requestedValues = pendingValues ;
        name = getName() + ";" + OPTION_PENDING_VALUES ;
    }
    else
    {
        requestedValues = activeValues ;
        name = getName() ;
    }

    if (isMultiValued())
    {
      long[] values = new long[requestedValues.size()];
      for (int i=0; i < values.length; i++)
      {
        values[i] = requestedValues.get(i);
      }

      return new javax.management.Attribute(name, values);
    }
    else
    {
      if (requestedValues.isEmpty())
      {
        return null;
      }
      else
      {
        return new javax.management.Attribute(name, requestedValues.get(0));
      }
    }
  }


  /**
   * Retrieves a JMX attribute containing the active value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the active value set for this
   *          configuration attribute, or <CODE>null</CODE> if it does not have
   *          any active values.
   */
  public javax.management.Attribute toJMXAttribute()
    {

        return _toJMXAttribute(false);
    }

  /**
   * Retrieves a JMX attribute containing the pending value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the pending value set for this
   *          configuration attribute.
   */
  public  javax.management.Attribute toJMXAttributePending()
  {
      return _toJMXAttribute(true);
  }



  /**
   * Adds information about this configuration attribute to the provided JMX
   * attribute list.  If this configuration attribute requires administrative
   * action before changes take effect and it has a set of pending values, then
   * two attributes should be added to the list -- one for the active value
   * and one for the pending value.  The pending value should be named with
   * the pending option.
   *
   * @param  attributeList  The attribute list to which the JMX attribute(s)
   *                        should be added.
   */
  public void toJMXAttribute(AttributeList attributeList)
  {
    if (activeValues.size() > 0)
    {
      if (isMultiValued())
      {
        long[] values = new long[activeValues.size()];
        for (int i=0; i < values.length; i++)
        {
          values[i] = activeValues.get(i);
        }

        attributeList.add(new javax.management.Attribute(getName(), values));
      }
      else
      {
        attributeList.add(new javax.management.Attribute(getName(),
                                                         activeValues.get(0)));
      }
    }
    else
    {
      if (isMultiValued())
      {
        attributeList.add(new javax.management.Attribute(getName(),
                                                         new String[0]));
      }
      else
      {
        attributeList.add(new javax.management.Attribute(getName(), null));
      }
    }


    if (requiresAdminAction() && (pendingValues != null) &&
        (pendingValues != activeValues))
    {
      String name = getName() + ";" + OPTION_PENDING_VALUES;

      if (isMultiValued())
      {
        long[] values = new long[pendingValues.size()];
        for (int i=0; i < values.length; i++)
        {
          values[i] = pendingValues.get(i);
        }

        attributeList.add(new javax.management.Attribute(name, values));
      }
      else if (! pendingValues.isEmpty())
      {
        attributeList.add(new javax.management.Attribute(name,
                                                         pendingValues.get(0)));
      }
    }
  }



  /**
   * Adds information about this configuration attribute to the provided list in
   * the form of a JMX <CODE>MBeanAttributeInfo</CODE> object.  If this
   * configuration attribute requires administrative action before changes take
   * effect and it has a set of pending values, then two attribute info objects
   * should be added to the list -- one for the active value (which should be
   * read-write) and one for the pending value (which should be read-only).  The
   * pending value should be named with the pending option.
   *
   * @param  attributeInfoList  The list to which the attribute information
   *                            should be added.
   */
  public void toJMXAttributeInfo(List<MBeanAttributeInfo> attributeInfoList)
  {
    if (isMultiValued())
    {
      attributeInfoList.add(new MBeanAttributeInfo(getName(),
                                                   JMX_TYPE_LONG_ARRAY,
                                                   String.valueOf(
                                                           getDescription()),
                                                   true, true, false));
    }
    else
    {
      attributeInfoList.add(new MBeanAttributeInfo(getName(),
                                                   Long.class.getName(),
                                                   String.valueOf(
                                                           getDescription()),
                                                   true, true, false));
    }


    if (requiresAdminAction())
    {
      String name = getName() + ";" + OPTION_PENDING_VALUES;

      if (isMultiValued())
      {
        attributeInfoList.add(new MBeanAttributeInfo(name, JMX_TYPE_LONG_ARRAY,
                                                     String.valueOf(
                                                             getDescription()),
                                                     true, false, false));
      }
      else
      {
        attributeInfoList.add(new MBeanAttributeInfo(name, Long.class.getName(),
                                                     String.valueOf(
                                                             getDescription()),
                                                     true, false, false));
      }
    }
  }



  /**
   * Retrieves a JMX <CODE>MBeanParameterInfo</CODE> object that describes this
   * configuration attribute.
   *
   * @return  A JMX <CODE>MBeanParameterInfo</CODE> object that describes this
   *          configuration attribute.
   */
  public MBeanParameterInfo toJMXParameterInfo()
  {
    if (isMultiValued())
    {
      return new MBeanParameterInfo(getName(), JMX_TYPE_LONG_ARRAY,
                                    String.valueOf(getDescription()));
    }
    else
    {
      return new MBeanParameterInfo(getName(), Long.TYPE.getName(),
                                    String.valueOf(getDescription()));
    }
  }



  /**
   * Attempts to set the value of this configuration attribute based on the
   * information in the provided JMX attribute.
   *
   * @param  jmxAttribute  The JMX attribute to use to attempt to set the value
   *                       of this configuration attribute.
   *
   * @throws  ConfigException  If the provided JMX attribute does not have an
   *                           acceptable value for this configuration
   *                           attribute.
   */
  public void setValue(javax.management.Attribute jmxAttribute)
         throws ConfigException
  {
    Object value = jmxAttribute.getValue();
    if (value instanceof Long)
    {
      setValue(((Long) value).longValue());
    }
    else if (value instanceof Integer)
    {
      setValue(((Integer) value).intValue());
    }
    else if (value instanceof String)
    {
      try
      {
        setValue(Long.parseLong((String) value));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_CONFIG_ATTR_INT_COULD_NOT_PARSE.get(
            String.valueOf(value), getName(), String.valueOf(e));
        throw new ConfigException(message, e);
      }
    }
    else if (value.getClass().isArray())
    {
      String componentType = value.getClass().getComponentType().getName();
      int length = Array.getLength(value);

      try
      {
        if (componentType.equals(Long.class.getName()))
        {
          ArrayList<Long> values = new ArrayList<Long>();

          for (int i=0; i < length; i++)
          {
            values.add(Array.getLong(value, i));
          }

          setValues(values);
        }
        else if (componentType.equals(Integer.class.getName()))
        {
          ArrayList<Long> values = new ArrayList<Long>();

          for (int i=0; i < length; i++)
          {
            values.add((long) Array.getInt(value, i));
          }

          setValues(values);
        }
        else if (componentType.equals(String.class.getName()))
        {
          ArrayList<Long> values = new ArrayList<Long>();

          for (int i=0; i < length; i++)
          {
            String s = (String) Array.get(value, i);
            values.add(Long.parseLong(s));
          }

          setValues(values);
        }
        else
        {
          Message message =
              ERR_CONFIG_ATTR_INT_INVALID_ARRAY_TYPE.get(
                      jmxAttribute.getName(), componentType);
          throw new ConfigException(message);
        }
      }
      catch (ConfigException ce)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ce);
        }

        throw ce;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_CONFIG_ATTR_INT_COULD_NOT_PARSE.get(
            componentType + "[" + length + "]", getName(), String.valueOf(e));
        throw new ConfigException(message, e);
      }
    }
    else
    {
      Message message = ERR_CONFIG_ATTR_INT_INVALID_TYPE.get(
          String.valueOf(value), getName(), value.getClass().getName());
      throw new ConfigException(message);
    }
  }



  /**
   * Creates a duplicate of this configuration attribute.
   *
   * @return  A duplicate of this configuration attribute.
   */
  public ConfigAttribute duplicate()
  {
    return new IntegerConfigAttribute(getName(), getDescription(), isRequired(),
                                      isMultiValued(), requiresAdminAction(),
                                      hasLowerBound, lowerBound, hasUpperBound,
                                      upperBound, activeValues, pendingValues);
  }
}

