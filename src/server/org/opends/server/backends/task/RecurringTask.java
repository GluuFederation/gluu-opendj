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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 */
package org.opends.server.backends.task;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import org.opends.messages.Message;



import java.util.Iterator;
import java.util.List;

import java.util.StringTokenizer;
import java.util.regex.Pattern;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attributes;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.RDN;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a information about a recurring task, which will be used
 * to repeatedly schedule tasks for processing.
 * <br>
 * It also provides some static methods that allow to validate strings in
 * crontab (5) format.
 */
public class RecurringTask
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The DN of the entry that actually defines this task.
  private final DN recurringTaskEntryDN;

  // The entry that actually defines this task.
  private final Entry recurringTaskEntry;

  // The unique ID for this recurring task.
  private final String recurringTaskID;

  // The fully-qualified name of the class that will be used to implement the
  // class.
  private final String taskClassName;

  // Task instance.
  private Task task;

  // Task scheduler for this task.
  private final TaskScheduler taskScheduler;

  // Number of tokens in the task schedule tab.
  private static final int TASKTAB_NUM_TOKENS = 5;

  // Maximum year month days.
  static final int MONTH_LENGTH[]
        = {31,28,31,30,31,30,31,31,30,31,30,31};

  // Maximum leap year month days.
  static final int LEAP_MONTH_LENGTH[]
        = {31,29,31,30,31,30,31,31,30,31,30,31};

  /**
   * Task tab fields.
   */
  private static enum TaskTab {MINUTE, HOUR, DAY, MONTH, WEEKDAY};

  private final static int MINUTE_INDEX = 0;
  private final static int HOUR_INDEX = 1;
  private final static int DAY_INDEX = 2;
  private final static int MONTH_INDEX = 3;
  private final static int WEEKDAY_INDEX = 4;

  // Exact match pattern.
  private static final Pattern exactPattern =
    Pattern.compile("\\d+");

  // Range match pattern.
  private static final Pattern rangePattern =
    Pattern.compile("\\d+[-]\\d+");

  // List match pattern.
  private static final Pattern listPattern =
    Pattern.compile("^(\\d+,)(.*)(\\d+)$");

  // Boolean arrays holding task tab slots.
  private final boolean[] minutesArray;
  private final boolean[] hoursArray;
  private final boolean[] daysArray;
  private final boolean[] monthArray;
  private final boolean[] weekdayArray;

  /**
   * Creates a new recurring task based on the information in the provided
   * entry.
   *
   * @param  taskScheduler       A reference to the task scheduler that may be
   *                             used to schedule new tasks.
   * @param  recurringTaskEntry  The entry containing the information to use to
   *                             define the task to process.
   *
   * @throws  DirectoryException  If the provided entry does not contain a valid
   *                              recurring task definition.
   */
  public RecurringTask(TaskScheduler taskScheduler, Entry recurringTaskEntry)
         throws DirectoryException
  {
    this.taskScheduler = taskScheduler;
    this.recurringTaskEntry = recurringTaskEntry;
    this.recurringTaskEntryDN = recurringTaskEntry.getDN();

    // Get the recurring task ID from the entry.  If there isn't one, then fail.
    AttributeType attrType = DirectoryServer.getAttributeType(
                                  ATTR_RECURRING_TASK_ID.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(
                                      ATTR_RECURRING_TASK_ID);
    }

    List<Attribute> attrList = recurringTaskEntry.getAttribute(attrType);
    if ((attrList == null) || attrList.isEmpty())
    {
      Message message =
          ERR_RECURRINGTASK_NO_ID_ATTRIBUTE.get(ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    if (attrList.size() > 1)
    {
      Message message =
          ERR_RECURRINGTASK_MULTIPLE_ID_TYPES.get(ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    Attribute attr = attrList.get(0);
    if (attr.isEmpty())
    {
      Message message = ERR_RECURRINGTASK_NO_ID.get(ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    Iterator<AttributeValue> iterator = attr.iterator();
    AttributeValue value = iterator.next();
    if (iterator.hasNext())
    {
      Message message =
          ERR_RECURRINGTASK_MULTIPLE_ID_VALUES.get(ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message);
    }

    recurringTaskID = value.getValue().toString();


    // Get the schedule for this task.
    attrType = DirectoryServer.getAttributeType(
                    ATTR_RECURRING_TASK_SCHEDULE.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(
        ATTR_RECURRING_TASK_SCHEDULE);
    }

    attrList = recurringTaskEntry.getAttribute(attrType);
    if ((attrList == null) || attrList.isEmpty())
    {
      Message message = ERR_RECURRINGTASK_NO_SCHEDULE_ATTRIBUTE.get(
          ATTR_RECURRING_TASK_SCHEDULE);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    if (attrList.size() > 1)
    {
      Message message = ERR_RECURRINGTASK_MULTIPLE_SCHEDULE_TYPES.get(
          ATTR_RECURRING_TASK_SCHEDULE);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    attr = attrList.get(0);
    if (attr.isEmpty())
    {
      Message message = ERR_RECURRINGTASK_NO_SCHEDULE_VALUES.get(
        ATTR_RECURRING_TASK_SCHEDULE);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    iterator = attr.iterator();
    value = iterator.next();
    if (iterator.hasNext())
    {
      Message message = ERR_RECURRINGTASK_MULTIPLE_SCHEDULE_VALUES.get(
          ATTR_RECURRING_TASK_SCHEDULE);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    String taskScheduleTab = value.toString();

    boolean[][] taskArrays = new boolean[][]{null, null, null, null, null};

    parseTaskTab(taskScheduleTab, taskArrays, true);

    minutesArray = taskArrays[MINUTE_INDEX];
    hoursArray = taskArrays[HOUR_INDEX];
    daysArray = taskArrays[DAY_INDEX];
    monthArray = taskArrays[MONTH_INDEX];
    weekdayArray = taskArrays[WEEKDAY_INDEX];

    // Get the class name from the entry.  If there isn't one, then fail.
    attrType = DirectoryServer.getAttributeType(
                    ATTR_TASK_CLASS.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(ATTR_TASK_CLASS);
    }

    attrList = recurringTaskEntry.getAttribute(attrType);
    if ((attrList == null) || attrList.isEmpty())
    {
      Message message = ERR_TASKSCHED_NO_CLASS_ATTRIBUTE.get(
          ATTR_TASK_CLASS);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    if (attrList.size() > 1)
    {
      Message message = ERR_TASKSCHED_MULTIPLE_CLASS_TYPES.get(
          ATTR_TASK_CLASS);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    attr = attrList.get(0);
    if (attr.isEmpty())
    {
      Message message =
          ERR_TASKSCHED_NO_CLASS_VALUES.get(ATTR_TASK_CLASS);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    iterator = attr.iterator();
    value = iterator.next();
    if (iterator.hasNext())
    {
      Message message = ERR_TASKSCHED_MULTIPLE_CLASS_VALUES.get(
          ATTR_TASK_CLASS);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    taskClassName = value.getValue().toString();


    // Make sure that the specified class can be loaded.
    Class<?> taskClass;
    try
    {
      taskClass = DirectoryServer.loadClass(taskClassName);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_RECURRINGTASK_CANNOT_LOAD_CLASS.
          get(String.valueOf(taskClassName), ATTR_TASK_CLASS,
              getExceptionMessage(e));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   e);
    }


    // Make sure that the specified class can be instantiated as a task.
    try
    {
      task = (Task) taskClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_RECURRINGTASK_CANNOT_INSTANTIATE_CLASS_AS_TASK.get(
          String.valueOf(taskClassName), Task.class.getName());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   e);
    }


    // Make sure that we can initialize the task with the information in the
    // provided entry.
    try
    {
      task.initializeTaskInternal(taskScheduler, recurringTaskEntry);
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      Message message = ERR_RECURRINGTASK_CANNOT_INITIALIZE_INTERNAL.get(
          String.valueOf(taskClassName), ie.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, ie);
    }

    task.initializeTask();
  }



  /**
   * Retrieves the unique ID assigned to this recurring task.
   *
   * @return  The unique ID assigned to this recurring task.
   */
  public String getRecurringTaskID()
  {
    return recurringTaskID;
  }



  /**
   * Retrieves the DN of the entry containing the data for this recurring task.
   *
   * @return  The DN of the entry containing the data for this recurring task.
   */
  public DN getRecurringTaskEntryDN()
  {
    return recurringTaskEntryDN;
  }



  /**
   * Retrieves the entry containing the data for this recurring task.
   *
   * @return  The entry containing the data for this recurring task.
   */
  public Entry getRecurringTaskEntry()
  {
    return recurringTaskEntry;
  }



  /**
   * Retrieves the fully-qualified name of the Java class that provides the
   * implementation logic for this recurring task.
   *
   * @return  The fully-qualified name of the Java class that provides the
   *          implementation logic for this recurring task.
   */
  public String getTaskClassName()
  {
    return taskClassName;
  }



  /**
   * Schedules the next iteration of this recurring task for processing.
   * @param  calendar date and time to schedule next iteration from.
   * @return The task that has been scheduled for processing.
   * @throws DirectoryException to indicate an error.
   */
  public Task scheduleNextIteration(GregorianCalendar calendar)
          throws DirectoryException
  {
    Task nextTask = null;
    Date nextTaskDate = null;

    try {
      nextTaskDate = getNextIteration(calendar);
    } catch (IllegalArgumentException e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
        ERR_RECURRINGTASK_INVALID_TOKENS_COMBO.get(
        ATTR_RECURRING_TASK_SCHEDULE));
    }

    SimpleDateFormat dateFormat = new SimpleDateFormat(
      DATE_FORMAT_COMPACT_LOCAL_TIME);
    String nextTaskStartTime = dateFormat.format(nextTaskDate);

    try {
      // Make a regular task iteration from this recurring task.
      nextTask = task.getClass().newInstance();
      Entry nextTaskEntry = recurringTaskEntry.duplicate(false);
      SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
      String nextTaskID = task.getTaskID() + "-" + df.format(nextTaskDate);
      String nextTaskIDName = NAME_PREFIX_TASK + "id";
      AttributeType taskIDAttrType =
        DirectoryServer.getAttributeType(nextTaskIDName);
      Attribute nextTaskIDAttr = Attributes.create(
        taskIDAttrType, nextTaskID);
      nextTaskEntry.replaceAttribute(nextTaskIDAttr);
      RDN nextTaskRDN = RDN.decode(nextTaskIDName + "=" + nextTaskID);
      DN nextTaskDN = new DN(nextTaskRDN,
        taskScheduler.getTaskBackend().getScheduledTasksParentDN());
      nextTaskEntry.setDN(nextTaskDN);

      String nextTaskStartTimeName = NAME_PREFIX_TASK +
        "scheduled-start-time";
      AttributeType taskStartTimeAttrType =
        DirectoryServer.getAttributeType(nextTaskStartTimeName);
      Attribute nextTaskStartTimeAttr = Attributes.create(
        taskStartTimeAttrType, nextTaskStartTime);
      nextTaskEntry.replaceAttribute(nextTaskStartTimeAttr);

      nextTask.initializeTaskInternal(taskScheduler, nextTaskEntry);
      nextTask.initializeTask();
    } catch (Exception e) {
      // Should not happen, debug log it otherwise.
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return nextTask;
  }

  /**
   * Parse and validate recurring task schedule.
   * @param taskSchedule recurring task schedule tab in crontab(5) format.
   * @throws DirectoryException to indicate an error.
   */
  public static void parseTaskTab(String taskSchedule) throws DirectoryException
  {
    parseTaskTab(taskSchedule, new boolean[][]{null, null, null, null, null},
        false);
  }

  /**
   * Parse and validate recurring task schedule.
   * @param taskSchedule recurring task schedule tab in crontab(5) format.
   * @param arrays, an array of 5 boolean arrays.  The array has the following
   * structure: {minutesArray, hoursArray, daysArray, monthArray, weekdayArray}.
   * @param referToTaskEntryAttribute whether the error messages must refer
   * to the task entry attribute or not.  This is used to have meaningful
   * messages when the {@link #parseTaskTab(String)} is called to validate
   * a crontab formatted string.
   * @throws DirectoryException to indicate an error.
   */
  private static void parseTaskTab(String taskSchedule,
      boolean[][] arrays,
      boolean referToTaskEntryAttribute) throws DirectoryException
  {
    StringTokenizer st = new StringTokenizer(taskSchedule);

    if (st.countTokens() != TASKTAB_NUM_TOKENS) {
      if (referToTaskEntryAttribute)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_RECURRINGTASK_INVALID_N_TOKENS.get(
                ATTR_RECURRING_TASK_SCHEDULE));
      }
      else
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_RECURRINGTASK_INVALID_N_TOKENS_SIMPLE.get());
      }
    }

    for (TaskTab taskTabToken : TaskTab.values()) {
      String token = st.nextToken();
      switch (taskTabToken) {
        case MINUTE:
          try {
            arrays[MINUTE_INDEX] = parseTaskTabField(token, 0, 59);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MINUTE_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MINUTE_TOKEN_SIMPLE.get());
            }
          }
          break;
        case HOUR:
          try {
            arrays[HOUR_INDEX] = parseTaskTabField(token, 0, 23);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_HOUR_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_HOUR_TOKEN_SIMPLE.get());
            }
          }
          break;
        case DAY:
          try {
            arrays[DAY_INDEX] = parseTaskTabField(token, 1, 31);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_DAY_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_DAY_TOKEN_SIMPLE.get());
            }
          }
          break;
        case MONTH:
          try {
            arrays[MONTH_INDEX] = parseTaskTabField(token, 1, 12);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MONTH_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MONTH_TOKEN_SIMPLE.get());
            }
          }
          break;
        case WEEKDAY:
          try {
            arrays[WEEKDAY_INDEX] = parseTaskTabField(token, 0, 6);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_WEEKDAY_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_WEEKDAY_TOKEN_SIMPLE.get());
            }
          }
          break;
      }
    }
  }

  /**
   * Parse and validate recurring task schedule field.
   * @param tabField recurring task schedule field in crontab(5) format.
   * @param minValue minimum value allowed for this field.
   * @param maxValue maximum value allowed for this field.
   * @return boolean schedule slots range set according to
   *         the schedule field.
   * @throws IllegalArgumentException if tab field is invalid.
   */
  public static boolean[] parseTaskTabField(String tabField,
    int minValue, int maxValue) throws IllegalArgumentException
  {
    boolean[] valueList = new boolean[maxValue + 1];
    Arrays.fill(valueList, false);

    // Blanket.
    if (tabField.equals("*")) {
      for (int i = minValue; i <= maxValue; i++) {
        valueList[i] = true;
      }
      return valueList;
    }

    // Exact.
    if (exactPattern.matcher(tabField).matches()) {
      int value = Integer.parseInt(tabField);
      if ((value >= minValue) && (value <= maxValue)) {
        valueList[value] = true;
        return valueList;
      }
      throw new IllegalArgumentException();
    }

    // Range.
    if (rangePattern.matcher(tabField).matches()) {
      StringTokenizer st = new StringTokenizer(tabField, "-");
      int startValue = Integer.parseInt(st.nextToken());
      int endValue = Integer.parseInt(st.nextToken());
      if ((startValue < endValue) &&
          ((startValue >= minValue) && (endValue <= maxValue)))
      {
        for (int i = startValue; i <= endValue; i++) {
          valueList[i] = true;
        }
        return valueList;
      }
      throw new IllegalArgumentException();
    }

    // List.
    if (listPattern.matcher(tabField).matches()) {
      StringTokenizer st = new StringTokenizer(tabField, ",");
      while (st.hasMoreTokens()) {
        int value = Integer.parseInt(st.nextToken());
        if ((value >= minValue) && (value <= maxValue)) {
          valueList[value] = true;
        } else {
          throw new IllegalArgumentException();
        }
      }
      return valueList;
    }

    throw new IllegalArgumentException();
  }

  /**
   * Get next recurring slot from the range.
   * @param timesList the range.
   * @param fromNow the current slot.
   * @return next recurring slot in the range.
   */
  private int getNextTimeSlice(boolean[] timesList, int fromNow)
  {
    for (int i = fromNow; i < timesList.length; i++) {
      if (timesList[i]) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Get next task iteration date according to recurring schedule.
   * @param  calendar date and time to schedule from.
   * @return next task iteration date.
   * @throws IllegalArgumentException if recurring schedule is invalid.
   */
  private Date getNextIteration(GregorianCalendar calendar)
          throws IllegalArgumentException
  {
    int minute, hour, day, month, weekday;
    calendar.setFirstDayOfWeek(GregorianCalendar.SUNDAY);
    calendar.add(GregorianCalendar.MINUTE, 1);
    calendar.set(GregorianCalendar.SECOND, 0);
    calendar.set(GregorianCalendar.MILLISECOND, 0);
    calendar.setLenient(false);

    // Weekday
    for (;;) {
      // Month
      for (;;) {
        // Day
        for (;;) {
          // Hour
          for (;;) {
            // Minute
            for (;;) {
              minute = getNextTimeSlice(minutesArray,
                calendar.get(GregorianCalendar.MINUTE));
              if (minute == -1) {
                calendar.set(GregorianCalendar.MINUTE, 0);
                calendar.add(GregorianCalendar.HOUR_OF_DAY, 1);
              } else {
                calendar.set(GregorianCalendar.MINUTE, minute);
                break;
              }
            }
            hour = getNextTimeSlice(hoursArray,
              calendar.get(GregorianCalendar.HOUR_OF_DAY));
            if (hour == -1) {
              calendar.set(GregorianCalendar.HOUR_OF_DAY, 0);
              calendar.add(GregorianCalendar.DAY_OF_MONTH, 1);
            } else {
              calendar.set(GregorianCalendar.HOUR_OF_DAY, hour);
              break;
            }
          }
          day = getNextTimeSlice(daysArray,
            calendar.get(GregorianCalendar.DAY_OF_MONTH));
          if ((day == -1) || (day > calendar.getActualMaximum(
                              GregorianCalendar.DAY_OF_MONTH)))
          {
            calendar.set(GregorianCalendar.DAY_OF_MONTH, 1);
            calendar.add(GregorianCalendar.MONTH, 1);
          } else {
            calendar.set(GregorianCalendar.DAY_OF_MONTH, day);
            break;
          }
        }
        month = getNextTimeSlice(monthArray,
          (calendar.get(GregorianCalendar.MONTH) + 1));
        if (month == -1) {
          calendar.set(GregorianCalendar.MONTH, 0);
          calendar.add(GregorianCalendar.YEAR, 1);
        } else {
          if ((day > LEAP_MONTH_LENGTH[month - 1]) &&
              ((getNextTimeSlice(daysArray, 1) != day) ||
               (getNextTimeSlice(monthArray, 1) != month)))
          {
            calendar.set(GregorianCalendar.DAY_OF_MONTH, 1);
            calendar.add(GregorianCalendar.MONTH, 1);
          } else if ((day > MONTH_LENGTH[month - 1]) &&
                     (!calendar.isLeapYear(calendar.get(
                      GregorianCalendar.YEAR)))) {
            calendar.add(GregorianCalendar.YEAR, 1);
          } else {
            calendar.set(GregorianCalendar.MONTH, (month - 1));
            break;
          }
        }
      }
      weekday = getNextTimeSlice(weekdayArray,
        (calendar.get(GregorianCalendar.DAY_OF_WEEK) - 1));
      if ((weekday == -1) ||
          (weekday != (calendar.get(
           GregorianCalendar.DAY_OF_WEEK) - 1)))
      {
        calendar.add(GregorianCalendar.DAY_OF_MONTH, 1);
      } else {
        break;
      }
    }

    return calendar.getTime();
  }
}
