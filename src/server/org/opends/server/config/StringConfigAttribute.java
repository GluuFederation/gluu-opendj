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
 * This class defines a string configuration attribute, which can hold zero or
 * more string values.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class StringConfigAttribute
       extends ConfigAttribute
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The set of active values for this attribute.
  private List<String> activeValues;

  // The set of pending values for this attribute.
  private List<String> pendingValues;



  /**
   * Creates a new string configuration attribute stub with the provided
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
   */
  public StringConfigAttribute(String name, Message description,
                               boolean isRequired, boolean isMultiValued,
                               boolean requiresAdminAction)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction);


    activeValues  = new ArrayList<String>();
    pendingValues = activeValues;
  }



  /**
   * Creates a new string configuration attribute with the provided information.
   * No validation will be performed on the provided value.
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
   * @param  value                The value for this string configuration
   *                              attribute.
   */
  public StringConfigAttribute(String name, Message description,
                               boolean isRequired, boolean isMultiValued,
                               boolean requiresAdminAction, String value)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction,
          getValueSet(value));


    if (value == null)
    {
      activeValues = new ArrayList<String>();
    }
    else
    {
      activeValues = new ArrayList<String>(1);
      activeValues.add(value);
    }

    pendingValues = activeValues;
  }



  /**
   * Creates a new string configuration attribute with the provided information.
   * No validation will be performed on the provided values.
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
   * @param  values               The set of values for this configuration
   *                              attribute.
   */
  public StringConfigAttribute(String name, Message description,
                               boolean isRequired, boolean isMultiValued,
                               boolean requiresAdminAction, List<String> values)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction,
          getValueSet(values));


    if (values == null)
    {
      activeValues  = new ArrayList<String>();
      pendingValues = activeValues;
    }
    else
    {
      activeValues  = values;
      pendingValues = activeValues;
    }
  }



  /**
   * Creates a new string configuration attribute with the provided information.
   * No validation will be performed on the provided values.
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
   * @param  activeValues         The set of active values for this
   *                              configuration attribute.
   * @param  pendingValues        The set of pending values for this
   *                              configuration attribute.
   */
  public StringConfigAttribute(String name, Message description,
                               boolean isRequired, boolean isMultiValued,
                               boolean requiresAdminAction,
                               List<String> activeValues,
                               List<String> pendingValues)
  {
    super(name, description, isRequired, isMultiValued, requiresAdminAction,
          getValueSet(activeValues), (pendingValues != null),
          getValueSet(pendingValues));


    if (activeValues == null)
    {
      this.activeValues = new ArrayList<String>();
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
    return "String";
  }



  /**
   * Retrieves the attribute syntax for this configuration attribute.
   *
   * @return  The attribute syntax for this configuration attribute.
   */
  public AttributeSyntax<?> getSyntax()
  {
    return DirectoryServer.getDefaultStringSyntax();
  }



  /**
   * Retrieves the active value for this configuration attribute as a string.
   * This is only valid for single-valued attributes that have a value.
   *
   * @return  The active value for this configuration attribute as a string.
   *
   * @throws  ConfigException  If this attribute does not have exactly one
   *                           active value.
   */
  public String activeValue()
         throws ConfigException
  {
    if ((activeValues == null) || activeValues.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_NO_STRING_VALUE.get(getName());
      throw new ConfigException(message);
    }

    if (activeValues.size() > 1)
    {
      Message message = ERR_CONFIG_ATTR_MULTIPLE_STRING_VALUES.get(getName());
      throw new ConfigException(message);
    }

    return activeValues.get(0);
  }



  /**
   * Retrieves the set of active values for this configuration attribute.
   *
   * @return  The set of active values for this configuration attribute.
   */
  public List<String> activeValues()
  {
    return activeValues;
  }



  /**
   * Retrieves the pending value for this configuration attribute as a string.
   * This is only valid for single-valued attributes that have a value.  If this
   * attribute does not have any pending values, then the active value will be
   * returned.
   *
   * @return  The pending value for this configuration attribute as a string.
   *
   * @throws  ConfigException  If this attribute does not have exactly one
   *                           pending value.
   */
  public String pendingValue()
         throws ConfigException
  {
    if (! hasPendingValues())
    {
      return activeValue();
    }

    if ((pendingValues == null) || pendingValues.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_NO_STRING_VALUE.get(getName());
      throw new ConfigException(message);
    }

    if (pendingValues.size() > 1)
    {
      Message message = ERR_CONFIG_ATTR_MULTIPLE_STRING_VALUES.get(getName());
      throw new ConfigException(message);
    }

    return pendingValues.get(0);
  }



  /**
   * Retrieves the set of pending values for this configuration attribute.  If
   * there are no pending values, then the set of active values will be
   * returned.
   *
   * @return  The set of pending values for this configuration attribute.
   */
  public List<String> pendingValues()
  {
    if (! hasPendingValues())
    {
      return activeValues;
    }

    return pendingValues;
  }



  /**
   * Sets the value for this string configuration attribute.
   *
   * @param  value  The value for this string configuration attribute.
   *
   * @throws  ConfigException  If the provided value is not acceptable.
   */
  public void setValue(String value)
         throws ConfigException
  {
    if ((value == null) || (value.length() == 0))
    {
      Message message = ERR_CONFIG_ATTR_EMPTY_STRING_VALUE.get(getName());
      throw new ConfigException(message);
    }

    if (requiresAdminAction())
    {
      pendingValues = new ArrayList<String>(1);
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
   * Sets the values for this string configuration attribute.
   *
   * @param  values  The set of values for this string configuration attribute.
   *
   * @throws  ConfigException  If the provided value set or any of the
   *                           individual values are not acceptable.
   */
  public void setValues(List<String> values)
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
          pendingValues = new ArrayList<String>();
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
    for (String value : values)
    {
      if ((value == null) || (value.length() == 0))
      {
        Message message = ERR_CONFIG_ATTR_EMPTY_STRING_VALUE.get(getName());
        throw new ConfigException(message);
      }

      AttributeValue attrValue =
          AttributeValues.create(ByteString.valueOf(value),
              ByteString.valueOf(value));

      if (valueSet.contains(attrValue))
      {
        Message message =
            ERR_CONFIG_ATTR_ADD_VALUES_ALREADY_EXISTS.get(getName(), value);
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
  private static LinkedHashSet<AttributeValue> getValueSet(String value)
  {
    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(1);

    valueSet.add(AttributeValues.create(ByteString.valueOf(value),
        ByteString.valueOf(value)));

    return valueSet;
  }



  /**
   * Creates the appropriate value set with the provided values.
   *
   * @param  values  The values to use to create the value set.
   *
   * @return  The constructed value set.
   */
  private static LinkedHashSet<AttributeValue> getValueSet(List<String> values)
  {
    if (values == null)
    {
      return null;
    }

    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(values.size());

    for (String value : values)
    {
      valueSet.add(AttributeValues.create(ByteString.valueOf(value),
          ByteString.valueOf(value)));
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
    // The only requirement is that the value is not null or empty.
    if ((value == null) || (value.getValue().toString().length() == 0))
    {
      rejectReason.append(ERR_CONFIG_ATTR_EMPTY_STRING_VALUE.get(getName()));
      return false;
    }


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
      if ((valueString == null) || (valueString.length() == 0))
      {
        Message message = ERR_CONFIG_ATTR_EMPTY_STRING_VALUE.get(getName());

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
    return activeValues;
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
      return pendingValues;
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
    ArrayList<String> activeValues  = null;
    ArrayList<String> pendingValues = null;

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
              pendingValues = new ArrayList<String>(0);
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

            pendingValues = new ArrayList<String>(numValues);
            for (AttributeValue v : a)
            {
              pendingValues.add(v.getValue().toString());
            }
          }
        }
        else
        {
          // This is illegal -- only the pending option is allowed for
          // configuration attributes.
          Message message =
              ERR_CONFIG_ATTR_OPTIONS_NOT_ALLOWED.get(a.getName());
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
            activeValues = new ArrayList<String>(0);
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

          activeValues = new ArrayList<String>(numValues);
          for (AttributeValue v : a)
          {
            activeValues.add(v.getValue().toString());
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

    return new StringConfigAttribute(getName(), getDescription(), isRequired(),
                                     isMultiValued(), requiresAdminAction(),
                                     activeValues, pendingValues);
  }



  /**
   * Retrieves a JMX attribute containing the active value set for this
   * configuration attribute.
   *
   * @param pending indicates if pending or active  values are required.
   *
   * @return  A JMX attribute containing the active value set for this
   *          configuration attribute, or <CODE>null</CODE> if it does not have
   *          any active values.
   */
  private javax.management.Attribute _toJMXAttribute(boolean pending)
  {
    List<String> requestedValues ;
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
      String[] values = new String[requestedValues.size()];
      requestedValues.toArray(values);

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
    return _toJMXAttribute(false) ;
  }

  /**
   * Retrieves a JMX attribute containing the pending value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the pending value set for this
   *          configuration attribute, or <CODE>null</CODE> if it does not have
   *          any active values.
   */
  public javax.management.Attribute toJMXAttributePending()
  {
    return _toJMXAttribute(true) ;
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
        String[] values = new String[activeValues.size()];
        activeValues.toArray(values);

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
        String[] values = new String[pendingValues.size()];
        pendingValues.toArray(values);

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
                                                   JMX_TYPE_STRING_ARRAY,
                                                   String.valueOf(
                                                           getDescription()),
                                                   true, true, false));
    }
    else
    {
      attributeInfoList.add(new MBeanAttributeInfo(getName(),
                                                   String.class.getName(),
                                                   String.valueOf(
                                                           getDescription()),
                                                   true, true, false));
    }


    if (requiresAdminAction())
    {
      String name = getName() + ";" + OPTION_PENDING_VALUES;

      if (isMultiValued())
      {
        attributeInfoList.add(new MBeanAttributeInfo(name,
                                                     JMX_TYPE_STRING_ARRAY,
                                                     String.valueOf(
                                                             getDescription()),
                                                     true, false, false));
      }
      else
      {
        attributeInfoList.add(new MBeanAttributeInfo(name,
                                                     String.class.getName(),
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
      return new MBeanParameterInfo(getName(), JMX_TYPE_STRING_ARRAY,
                                    String.valueOf(getDescription()));
    }
    else
    {
      return new MBeanParameterInfo(getName(), String.class.getName(),
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
    if (value instanceof String)
    {
      setValue((String) value);
    }
    else if (value.getClass().isArray())
    {
      String componentType = value.getClass().getComponentType().getName();
      int length = Array.getLength(value);

      if (componentType.equals(String.class.getName()))
      {
        try
        {
          ArrayList<String> values = new ArrayList<String>(length);

          for (int i=0; i < length; i++)
          {
            values.add((String) Array.get(value, i));
          }

          setValues(values);
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

          Message message = ERR_CONFIG_ATTR_INVALID_STRING_VALUE.get(
              getName(), String.valueOf(value), String.valueOf(e));
          throw new ConfigException(message, e);
        }
      }
      else
      {
        Message message =
            ERR_CONFIG_ATTR_STRING_INVALID_ARRAY_TYPE.get(
                    String.valueOf(jmxAttribute),
                    String.valueOf(componentType));
        throw new ConfigException(message);
      }
    }
    else
    {
      Message message = ERR_CONFIG_ATTR_STRING_INVALID_TYPE.get(
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
    return new StringConfigAttribute(getName(), getDescription(), isRequired(),
                                     isMultiValued(), requiresAdminAction(),
                                     activeValues, pendingValues);
  }
}

