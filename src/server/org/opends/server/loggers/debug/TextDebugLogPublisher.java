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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.loggers.debug;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.DebugLogPublisherCfgDefn;
import org.opends.server.admin.std.server.DebugTargetCfg;
import org.opends.server.admin.std.server.FileBasedDebugLogPublisherCfg;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.api.DirectoryThread;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.*;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

import com.sleepycat.je.*;

/**
 * The debug log publisher implementation that writes debug messages to files
 * on disk. It also maintains the rotation and retention polices of the log
 * files.
 */
public class TextDebugLogPublisher
    extends DebugLogPublisher<FileBasedDebugLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedDebugLogPublisherCfg>,
               ConfigurationAddListener<DebugTargetCfg>,
               ConfigurationDeleteListener<DebugTargetCfg>
{
  private static long globalSequenceNumber;

  private TextWriter writer;

  private FileBasedDebugLogPublisherCfg currentConfig;

  /**
   * Returns an instance of the text debug log publisher that will print
   * all messages to the provided writer. This is used to print the messages
   * to the console when the server starts up. By default, only error level
   * messages are printed. Special debug targets are also parsed from
   * system properties if any are specified.
   *
   * @param writer The text writer where the message will be written to.
   * @return The instance of the text error log publisher that will print
   * all messages to standard out.
   */
  public static TextDebugLogPublisher
      getStartupTextDebugPublisher(TextWriter writer)
  {
    TextDebugLogPublisher startupPublisher = new TextDebugLogPublisher();
    startupPublisher.writer = writer;

    Set<Map.Entry<Object, Object>> propertyEntries =
        System.getProperties().entrySet();
    for(Map.Entry<Object, Object> entry : propertyEntries)
    {
      if(((String)entry.getKey()).startsWith(PROPERTY_DEBUG_TARGET))
      {
        String value = (String)entry.getValue();
        int settingsStart= value.indexOf(":");

        //See if the scope and settings exists
        if(settingsStart > 0)
        {
          String scope = value.substring(0, settingsStart);
          TraceSettings settings =
              TraceSettings.parseTraceSettings(
                  value.substring(settingsStart+1));
          if(settings != null)
          {
            startupPublisher.addTraceSettings(scope, settings);
          }
        }
      }
    }

    return startupPublisher;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAcceptable(
      FileBasedDebugLogPublisherCfg config, List<Message> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeLogPublisher(FileBasedDebugLogPublisherCfg config)
      throws ConfigException, InitializationException
  {
    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm =
          FilePermission.decodeUNIXMode(config.getLogFilePermissions());

      LogPublisherErrorHandler errorHandler =
          new LogPublisherErrorHandler(config.dn());

      boolean writerAutoFlush =
          config.isAutoFlush() && !config.isAsynchronous();

      MultifileTextWriter writer =
          new MultifileTextWriter("Multifile Text Writer for " +
              config.dn().toNormalizedString(),
                                  config.getTimeInterval(),
                                  fnPolicy,
                                  perm,
                                  errorHandler,
                                  "UTF-8",
                                  writerAutoFlush,
                                  config.isAppend(),
                                  (int)config.getBufferSize());

      // Validate retention and rotation policies.
      for(DN dn : config.getRotationPolicyDNs())
      {
        writer.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
      }

      for(DN dn: config.getRetentionPolicyDNs())
      {
        writer.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
      }

      if(config.isAsynchronous())
      {
        this.writer = new AsynchronousTextWriter(
            "Asynchronous Text Writer for " + config.dn().toNormalizedString(),
            config.getQueueSize(), config.isAutoFlush(), writer);
      }
      else
      {
        this.writer = writer;
      }
    }
    catch(DirectoryException e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn().toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }
    catch(IOException e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_OPEN_FILE.get(
          logFile.toString(), config.dn().toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }


    config.addDebugTargetAddListener(this);
    config.addDebugTargetDeleteListener(this);

    //Get the default/global settings
    LogLevel logLevel =
        DebugLogLevel.parse(config.getDefaultDebugLevel().toString());
    Set<LogCategory> logCategories = null;
    if(!config.getDefaultDebugCategory().isEmpty())
    {
      logCategories =
          new HashSet<LogCategory>(config.getDefaultDebugCategory().size());
      for(DebugLogPublisherCfgDefn.DefaultDebugCategory category :
          config.getDefaultDebugCategory())
      {
        logCategories.add(DebugLogCategory.parse(category.toString()));
      }
    }

    TraceSettings defaultSettings =
        new TraceSettings(logLevel, logCategories,
                          config.isDefaultOmitMethodEntryArguments(),
                          config.isDefaultOmitMethodReturnValue(),
                          config.getDefaultThrowableStackFrames(),
                          config.isDefaultIncludeThrowableCause());

    addTraceSettings(null, defaultSettings);

    for(String name : config.listDebugTargets())
    {
      DebugTargetCfg targetCfg = config.getDebugTarget(name);

      addTraceSettings(targetCfg.getDebugScope(), new TraceSettings(targetCfg));
    }

    currentConfig = config;

    config.addFileBasedDebugChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      FileBasedDebugLogPublisherCfg config, List<Message> unacceptableReasons)
  {
    // Make sure the permission is valid.
    try
    {
      FilePermission filePerm =
          FilePermission.decodeUNIXMode(config.getLogFilePermissions());
      if(!filePerm.isOwnerWritable())
      {
        Message message = ERR_CONFIG_LOGGING_INSANE_MODE.get(
            config.getLogFilePermissions());
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch(DirectoryException e)
    {
      Message message = ERR_CONFIG_LOGGING_MODE_INVALID.get(
          config.getLogFilePermissions(), String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      FileBasedDebugLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    //Get the default/global settings
    LogLevel logLevel =
        DebugLogLevel.parse(config.getDefaultDebugLevel().toString());
    Set<LogCategory> logCategories = null;
    if(!config.getDefaultDebugCategory().isEmpty())
    {
      logCategories =
          new HashSet<LogCategory>(config.getDefaultDebugCategory().size());
      for(DebugLogPublisherCfgDefn.DefaultDebugCategory category :
          config.getDefaultDebugCategory())
      {
        logCategories.add(DebugLogCategory.parse(category.toString()));
      }
    }

    TraceSettings defaultSettings =
        new TraceSettings(logLevel, logCategories,
                          config.isDefaultOmitMethodEntryArguments(),
                          config.isDefaultOmitMethodReturnValue(),
                          config.getDefaultThrowableStackFrames(),
                          config.isDefaultIncludeThrowableCause());

    addTraceSettings(null, defaultSettings);

    DebugLogger.updateTracerSettings();

    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm =
          FilePermission.decodeUNIXMode(config.getLogFilePermissions());

      boolean writerAutoFlush =
          config.isAutoFlush() && !config.isAsynchronous();

      TextWriter currentWriter;
      // Determine the writer we are using. If we were writing asyncronously,
      // we need to modify the underlaying writer.
      if(writer instanceof AsynchronousTextWriter)
      {
        currentWriter = ((AsynchronousTextWriter)writer).getWrappedWriter();
      }
      else
      {
        currentWriter = writer;
      }

      if(currentWriter instanceof MultifileTextWriter)
      {
        MultifileTextWriter mfWriter = (MultifileTextWriter)writer;

        mfWriter.setNamingPolicy(fnPolicy);
        mfWriter.setFilePermissions(perm);
        mfWriter.setAppend(config.isAppend());
        mfWriter.setAutoFlush(writerAutoFlush);
        mfWriter.setBufferSize((int)config.getBufferSize());
        mfWriter.setInterval(config.getTimeInterval());

        mfWriter.removeAllRetentionPolicies();
        mfWriter.removeAllRotationPolicies();

        for(DN dn : config.getRotationPolicyDNs())
        {
          mfWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
        }

        for(DN dn: config.getRetentionPolicyDNs())
        {
          mfWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
        }

        if(writer instanceof AsynchronousTextWriter && !config.isAsynchronous())
        {
          // The asynchronous setting is being turned off.
          AsynchronousTextWriter asyncWriter =
              ((AsynchronousTextWriter)writer);
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if(!(writer instanceof AsynchronousTextWriter) &&
            config.isAsynchronous())
        {
          // The asynchronous setting is being turned on.
          writer = new AsynchronousTextWriter("Asynchronous Text Writer for " +
              config.dn().toNormalizedString(), config.getQueueSize(),
                                                config.isAutoFlush(),
                                                mfWriter);
        }

        if((currentConfig.isAsynchronous() && config.isAsynchronous()) &&
            (currentConfig.getQueueSize() != config.getQueueSize()))
        {
          adminActionRequired = true;
        }

        currentConfig = config;
      }
    }
    catch(Exception e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
              config.dn().toString(),
              stackTraceToSingleLineString(e));
      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(message);

    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAddAcceptable(DebugTargetCfg config,
                                              List<Message> unacceptableReasons)
  {
    return getTraceSettings(config.getDebugScope()) == null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationDeleteAcceptable(DebugTargetCfg config,
                                              List<Message> unacceptableReasons)
  {
    // A delete should always be acceptable.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationAdd(DebugTargetCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    addTraceSettings(config.getDebugScope(), new TraceSettings(config));

    DebugLogger.updateTracerSettings();

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationDelete(DebugTargetCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    removeTraceSettings(config.getDebugScope());

    DebugLogger.updateTracerSettings();

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceConstructor(LogLevel level,
                               TraceSettings settings,
                               String signature,
                               String sourceLocation,
                               Object[] args,
                               StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.CONSTRUCTOR;

    String msg = "";
    if(args != null)
    {
      msg = buildDefaultEntryMessage(args);
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceMethodEntry(LogLevel level,
                               TraceSettings settings,
                               String signature,
                               String sourceLocation,
                               Object obj,
                               Object[] args,
                               StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.ENTER;
    String msg = "";
    if(args != null)
    {
      msg = buildDefaultEntryMessage(args);
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceStaticMethodEntry(LogLevel level,
                                     TraceSettings settings,
                                     String signature,
                                     String sourceLocation,
                                     Object[] args,
                                     StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.ENTER;
    String msg = "";
    if(args != null)
    {
      msg = buildDefaultEntryMessage(args);
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceReturn(LogLevel level,
                          TraceSettings settings,
                          String signature,
                          String sourceLocation,
                          Object ret,
                          StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.EXIT;
    String msg = "";
    if(ret != null)
    {
      msg = DebugMessageFormatter.format("returned={%s}",
                                         new Object[] {ret});
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceThrown(LogLevel level,
                          TraceSettings settings,
                          String signature,
                          String sourceLocation,
                          Throwable ex,
                          StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.THROWN;

    String msg = DebugMessageFormatter.format("thrown={%s}",
                                              new Object[] {ex});

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(ex,
                                                        settings.stackDepth,
                                                        settings.includeCause);
    }
    publish(category, level, signature, sourceLocation, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceMessage(LogLevel level,
                           TraceSettings settings,
                           String signature,
                           String sourceLocation,
                           String msg,
                           StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.MESSAGE;

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceCaught(LogLevel level,
                          TraceSettings settings,
                          String signature,
                          String sourceLocation,
                          Throwable ex,
                          StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.CAUGHT;
    String msg = DebugMessageFormatter.format("caught={%s}",
                                              new Object[] {ex});

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(ex,
                                                        settings.stackDepth,
                                                        settings.includeCause);
    }
    publish(category, level, signature, sourceLocation, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceJEAccess(LogLevel level,
                            TraceSettings settings,
                            String signature,
                            String sourceLocation,
                            OperationStatus status,
                            Database database, Transaction txn,
                            DatabaseEntry key, DatabaseEntry data,
                            StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.DATABASE_ACCESS;

    // Build the string that is common to category DATABASE_ACCESS.
    StringBuilder builder = new StringBuilder();
    builder.append(" (");
    builder.append(status.toString());
    builder.append(")");
    builder.append(" db=");
    try
    {
      builder.append(database.getDatabaseName());
    }
    catch(DatabaseException de)
    {
      builder.append(de.toString());
    }
    if (txn != null)
    {
      builder.append(" txnid=");
      try
      {
        builder.append(txn.getId());
      }
      catch(DatabaseException de)
      {
        builder.append(de.toString());
      }
    }
    else
    {
      builder.append(" txnid=none");
    }

    builder.append(ServerConstants.EOL);
    if(key != null)
    {
      builder.append("key:");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
    }

    // If the operation was successful we log the same common information
    // plus the data
    if (status == OperationStatus.SUCCESS && data != null)
    {

      builder.append("data(len=");
      builder.append(data.getSize());
      builder.append("):");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, data.getData(), 4);

    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation,
            builder.toString(), stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceData(LogLevel level,
                        TraceSettings settings,
                        String signature,
                        String sourceLocation,
                        byte[] data,
                        StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.DATA;
    if(data != null)
    {
      StringBuilder builder = new StringBuilder();
      builder.append(ServerConstants.EOL);
      builder.append("data(len=");
      builder.append(data.length);
      builder.append("):");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, data, 4);

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation,
            builder.toString(), stack);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void traceProtocolElement(LogLevel level,
                                   TraceSettings settings,
                                   String signature,
                                   String sourceLocation,
                                   String decodedForm,
                                   StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.PROTOCOL;

    StringBuilder builder = new StringBuilder();
    builder.append(ServerConstants.EOL);
    builder.append(decodedForm);

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                        settings.stackDepth);
    }
    publish(category, level, signature, sourceLocation,
            builder.toString(), stack);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close()
  {
    writer.shutdown();

    if(currentConfig != null)
    {
      currentConfig.removeFileBasedDebugChangeListener(this);
    }
  }


  // Publishes a record, optionally performing some "special" work:
  // - injecting a stack trace into the message
  // - format the message with argument values
  private void publish(LogCategory category, LogLevel level, String signature,
                       String sourceLocation, String msg, String stack)
  {
    Thread thread = Thread.currentThread();

    StringBuilder buf = new StringBuilder();
    // Emit the timestamp.
    buf.append("[");
    buf.append(TimeThread.getLocalTime());
    buf.append("] ");

    // Emit the seq num
    buf.append(globalSequenceNumber++);
    buf.append(" ");

    // Emit debug category.
    buf.append(category);
    buf.append(" ");

    // Emit the debug level.
    buf.append(level);
    buf.append(" ");

    // Emit thread info.
    buf.append("thread={");
    buf.append(thread.getName());
    buf.append("(");
    buf.append(thread.getId());
    buf.append(")} ");

    if(thread instanceof DirectoryThread)
    {
      buf.append("threadDetail={");
      for (Map.Entry<String, String> entry :
        ((DirectoryThread) thread).getDebugProperties().entrySet())
      {
        buf.append(entry.getKey());
        buf.append("=");
        buf.append(entry.getValue());
        buf.append(" ");
      }
      buf.append("} ");
    }

    // Emit method info.
    buf.append("method={");
    buf.append(signature);
    buf.append("(");
    buf.append(sourceLocation);
    buf.append(")} ");

    // Emit message.
    buf.append(msg);

    // Emit Stack Trace.
    if(stack != null)
    {
      buf.append("\nStack Trace:\n");
      buf.append(stack);
    }

    writer.writeRecord(buf.toString());
  }

  private String buildDefaultEntryMessage(Object[] args)
  {
    StringBuilder format = new StringBuilder();
    for (int i = 0; i < args.length; i++)
    {
      if (i != 0) format.append(", ");
      format.append("arg");
      format.append(i + 1);
      format.append("={%s}");
    }

    return DebugMessageFormatter.format(format.toString(), args);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getDN()
  {
    if(currentConfig != null)
    {
      return currentConfig.dn();
    }
    else
    {
      return null;
    }
  }
}
