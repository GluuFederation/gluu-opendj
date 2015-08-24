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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.monitors;

import static org.opends.server.util.CollectionUtils.*;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.StackTraceMonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.InitializationException;

/**
 * This class defines a Directory Server monitor provider that can be used to
 * obtain a stack trace from all server threads that are currently defined in
 * the JVM.
 */
public class StackTraceMonitorProvider
       extends MonitorProvider<StackTraceMonitorProviderCfg>
{
  @Override
  public void initializeMonitorProvider(
                   StackTraceMonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }

  @Override
  public String getMonitorInstanceName()
  {
    return "JVM Stack Trace";
  }

  @Override
  public List<Attribute> getMonitorData()
  {
    Map<Thread,StackTraceElement[]> threadStacks = Thread.getAllStackTraces();

    // Re-arrange all of the elements by thread ID so that there is some logical order.
    TreeMap<Long,Map.Entry<Thread,StackTraceElement[]>> orderedStacks = new TreeMap<>();
    for (Map.Entry<Thread,StackTraceElement[]> e : threadStacks.entrySet())
    {
      orderedStacks.put(e.getKey().getId(), e);
    }

    AttributeType attrType =
         DirectoryServer.getDefaultAttributeType("jvmThread");
    AttributeBuilder builder = new AttributeBuilder(attrType);
    for (Map.Entry<Thread,StackTraceElement[]> e : orderedStacks.values())
    {
      Thread t                          = e.getKey();
      StackTraceElement[] stackElements = e.getValue();

      long id = t.getId();
      builder.add("id=" + id + " ---------- " + t.getName() + " ----------");

      // Create an attribute for the stack trace.
      if (stackElements != null)
      {
        for (int j=0; j < stackElements.length; j++)
        {
          StringBuilder buffer = new StringBuilder();
          buffer.append("id=");
          buffer.append(id);
          buffer.append(" frame[");
          buffer.append(j);
          buffer.append("]=");

          buffer.append(stackElements[j].getClassName());
          buffer.append(".");
          buffer.append(stackElements[j].getMethodName());
          buffer.append("(");
          buffer.append(stackElements[j].getFileName());
          buffer.append(":");
          if (stackElements[j].isNativeMethod())
          {
            buffer.append("native");
          }
          else
          {
            buffer.append(stackElements[j].getLineNumber());
          }
          buffer.append(")");

          builder.add(buffer.toString());
        }
      }
    }

    return newArrayList(builder.toAttribute());
  }
}
