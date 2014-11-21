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
package org.opends.server.monitors;



import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.ArrayList;

import org.opends.server.admin.std.server.VersionMonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.util.DynamicConstants;



/**
 * This class defines a monitor provider that reports Directory Server version
 * information.
 */
public class VersionMonitorProvider
       extends MonitorProvider<VersionMonitorProviderCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The name of the attribute used to provide the product name.
   */
  public static final String ATTR_PRODUCT_NAME = "productName";



  /**
   * The name of the attribute used to provide the short name.
   */
  public static final String ATTR_SHORT_NAME = "shortName";



  /**
   * The name of the attribute used to provide the major version number.
   */
  public static final String ATTR_MAJOR_VERSION = "majorVersion";



  /**
   * The name of the attribute used to provide the minor version number.
   */
  public static final String ATTR_MINOR_VERSION = "minorVersion";



  /**
   * The name of the attribute used to provide the point version number.
   */
  public static final String ATTR_POINT_VERSION = "pointVersion";



  /**
   * The name of the attribute used to provide the version qualifier string.
   */
  public static final String ATTR_VERSION_QUALIFIER = "versionQualifier";



  /**
   * The name of the attribute used to provide the weekly build number.
   */
  public static final String ATTR_BUILD_NUMBER = "buildNumber";



  /**
   * The name of the attribute used to provide the list of bugfix IDs.
   */
  public static final String ATTR_FIX_IDS = "fixIDs";



  /**
   * The name of the attribute used to provide the Subversion revision number.
   */
  public static final String ATTR_REVISION_NUMBER = "revisionNumber";



  /**
   * The name of the attribute used to provide the build ID (aka the build
   * timestamp).
   */
  public static final String ATTR_BUILD_ID = "buildID";



  /**
   * The name of the attribute used to provide the compact version string.
   */
  public static final String ATTR_COMPACT_VERSION = "compactVersion";



  /**
   * The name of the attribute used to provide the full version string.
   */
  public static final String ATTR_FULL_VERSION = "fullVersion";



  /**
   * {@inheritDoc}
   */
  public void initializeMonitorProvider(VersionMonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  public String getMonitorInstanceName()
  {
    return "Version";
  }


  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>(12);

    attrs.add(createAttribute(ATTR_PRODUCT_NAME,
                              DynamicConstants.PRODUCT_NAME));
    attrs.add(createAttribute(ATTR_SHORT_NAME, DynamicConstants.SHORT_NAME));
    attrs.add(createAttribute(ATTR_MAJOR_VERSION,
                              String.valueOf(DynamicConstants.MAJOR_VERSION)));
    attrs.add(createAttribute(ATTR_MINOR_VERSION,
                              String.valueOf(DynamicConstants.MINOR_VERSION)));
    attrs.add(createAttribute(ATTR_POINT_VERSION,
                              String.valueOf(DynamicConstants.POINT_VERSION)));

    String versionQualifier = DynamicConstants.VERSION_QUALIFIER;
    if ((versionQualifier != null) && (versionQualifier.length() > 0))
    {
      attrs.add(createAttribute(ATTR_VERSION_QUALIFIER, versionQualifier));
    }

    int buildNumber = DynamicConstants.BUILD_NUMBER;
    if (buildNumber > 0)
    {
      attrs.add(createAttribute(ATTR_BUILD_NUMBER,
                                String.valueOf(buildNumber)));
    }

    String fixIDs = DynamicConstants.FIX_IDS;
    if ((fixIDs != null) && (fixIDs.length() > 0))
    {
      attrs.add(createAttribute(ATTR_FIX_IDS, fixIDs));
    }

    attrs.add(createAttribute(ATTR_REVISION_NUMBER,
                   String.valueOf(DynamicConstants.REVISION_NUMBER)));
    attrs.add(createAttribute(ATTR_BUILD_ID, DynamicConstants.BUILD_ID));
    attrs.add(createAttribute(ATTR_COMPACT_VERSION,
                              DynamicConstants.COMPACT_VERSION_STRING));
    attrs.add(createAttribute(ATTR_FULL_VERSION,
                              DynamicConstants.FULL_VERSION_STRING));

    return attrs;
  }



  /**
   * Constructs an attribute using the provided information.  It will have the
   * default syntax.
   *
   * @param  name   The name to use for the attribute.
   * @param  value  The value to use for the attribute.
   *
   * @return  The attribute created from the provided information.
   */
  private Attribute createAttribute(String name, String value)
  {
    AttributeType attrType = DirectoryServer.getDefaultAttributeType(name);

    AttributeBuilder builder = new AttributeBuilder(attrType);

    builder.add(AttributeValues.create(attrType, value));

    return builder.toAttribute();
  }
}

