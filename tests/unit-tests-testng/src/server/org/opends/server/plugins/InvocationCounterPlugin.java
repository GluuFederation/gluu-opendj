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
package org.opends.server.plugins;



import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.*;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.operation.*;
import org.opends.messages.Message;


/**
 * This class defines a very simple plugin that simply increments a counter each
 * time a plugin is called.  There will be separate counters for each basic
 * type of plugin, including:
 * <BR>
 * <UL>
 *   <LI>pre-parse</LI>
 *   <LI>pre-operation</LI>
 *   <LI>post-operation</LI>
 *   <LI>post-response</LI>
 *   <LI>search result entry</LI>
 *   <LI>search result reference</LI>
 *   <LI>intermediate response</LI>
 *   <LI>post-connect</LI>
 *   <LI>post-disconnect</LI>
 *   <LI>LDIF import</LI>
 *   <LI>LDIF export</LI>
 *   <LI>startup</LI>
 *   <LI>shutdown</LI>
 * </UL>
 */
public class InvocationCounterPlugin
       extends DirectoryServerPlugin<PluginCfg>
{
  // Define the counters that will be used to keep track of everything.
  private static AtomicInteger preParseCounter        = new AtomicInteger(0);
  private static AtomicInteger preOperationCounter    = new AtomicInteger(0);
  private static AtomicInteger postOperationCounter   = new AtomicInteger(0);
  private static AtomicInteger postResponseCounter    = new AtomicInteger(0);
  private static AtomicInteger postSynchronizationCounter =
                                    new AtomicInteger(0);
  private static AtomicInteger searchEntryCounter     = new AtomicInteger(0);
  private static AtomicInteger searchReferenceCounter = new AtomicInteger(0);
  private static AtomicInteger subordinateModifyDNCounter =
                                    new AtomicInteger(0);
  private static AtomicInteger intermediateResponseCounter =
                                    new AtomicInteger(0);
  private static AtomicInteger postConnectCounter     = new AtomicInteger(0);
  private static AtomicInteger postDisconnectCounter  = new AtomicInteger(0);
  private static AtomicInteger ldifImportCounter      = new AtomicInteger(0);
  private static AtomicInteger ldifExportCounter      = new AtomicInteger(0);
  private static boolean       startupCalled          = false;
  private static boolean       shutdownCalled         = false;



  /**
   * Creates a new instance of this Directory Server plugin.  Every
   * plugin must implement a default constructor (it is the only one
   * that will be used to create plugins defined in the
   * configuration), and every plugin constructor must call
   * <CODE>super()</CODE> as its first element.
   */
  public InvocationCounterPlugin()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePlugin(Set<PluginType> pluginTypes,
                               PluginCfg configuration)
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseAbandonOperation abandonOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse doPreParse(PreParseAddOperation addOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse doPreParse(PreParseBindOperation bindOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseCompareOperation compareOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseDeleteOperation deleteOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseExtendedOperation extendedOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseModifyOperation modifyOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseModifyDNOperation modifyDNOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseSearchOperation searchOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseUnbindOperation unbindOperation)
  {
    preParseCounter.incrementAndGet();
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * Retrieves the number of times that the pre-parse plugins have been called
   * since the last reset.
   *
   * @return  The number of times that the pre-parse plugins have been called
   *          since the last reset.
   */
  public static int getPreParseCount()
  {
    return preParseCounter.get();
  }



  /**
   * Resets the pre-parse plugin invocation count to zero.
   *
   * @return  The pre-parse plugin invocation count before it was reset.
   */
  public static int resetPreParseCount()
  {
    return preParseCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationAddOperation addOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationBindOperation bindOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationCompareOperation compareOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationDeleteOperation deleteOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationExtendedOperation extendedOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationSearchOperation searchOperation)
  {
    preOperationCounter.incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * Retrieves the number of times that the pre-operation plugins have been
   * called since the last reset.
   *
   * @return  The number of times that the pre-operation plugins have been
   *          called since the last reset.
   */
  public static int getPreOperationCount()
  {
    return preOperationCounter.get();
  }



  /**
   * Resets the pre-operation plugin invocation count to zero.
   *
   * @return  The pre-operation plugin invocation count before it was reset.
   */
  public static int resetPreOperationCount()
  {
    return preOperationCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationAbandonOperation abandonOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationAddOperation addOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationBindOperation bindOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationCompareOperation compareOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationExtendedOperation extendedOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationSearchOperation searchOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationUnbindOperation unbindOperation)
  {
    postOperationCounter.incrementAndGet();
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * Retrieves the number of times that the post-operation plugins have been
   * called since the last reset.
   *
   * @return  The number of times that the post-operation plugins have been
   *          called since the last reset.
   */
  public static int getPostOperationCount()
  {
    return postOperationCounter.get();
  }



  /**
   * Resets the post-operation plugin invocation count to zero.
   *
   * @return  The post-operation plugin invocation count before it was reset.
   */
  public static int resetPostOperationCount()
  {
    return postOperationCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseAddOperation addOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseBindOperation bindOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseCompareOperation compareOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseDeleteOperation deleteOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseExtendedOperation extendedOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseModifyOperation modifyOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseModifyDNOperation modifyDNOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseSearchOperation searchOperation)
  {
    postResponseCounter.incrementAndGet();
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * Retrieves the number of times that the post-response plugins have been
   * called since the last reset.
   *
   * @return  The number of times that the post-response plugins have been
   *          called since the last reset.
   */
  public static int getPostResponseCount()
  {
    return postResponseCounter.get();
  }



  /**
   * Resets the post-response plugin invocation count to zero.
   *
   * @return  The post-response plugin invocation count before it was reset.
   */
  public static int resetPostResponseCount()
  {
    return postResponseCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void doPostSynchronization(PostSynchronizationAddOperation
                                         addOperation)
  {
    postSynchronizationCounter.incrementAndGet();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void doPostSynchronization(PostSynchronizationModifyOperation
                                         modifyOperation)
  {
    postSynchronizationCounter.incrementAndGet();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void doPostSynchronization(PostSynchronizationModifyDNOperation
                                         modifyDNOperation)
  {
    postSynchronizationCounter.incrementAndGet();
  }



  /**
   * Retrieves the number of times that the post-synchronization plugins have
   * been called since the last reset.
   *
   * @return  The number of times that the post-synchronization plugins have
   *          been called since the last reset.
   */
  public static int getPostSynchronizationCount()
  {
    return postSynchronizationCounter.get();
  }



  /**
   * Resets the post-synchronization plugin invocation count to zero.
   *
   * @return  The post-synchronization plugin invocation count before it was
   *          reset.
   */
  public static int resetPostSynchronizationCount()
  {
    return postSynchronizationCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.IntermediateResponse
       processSearchEntry(SearchEntrySearchOperation searchOperation,
                          SearchResultEntry searchEntry)
  {
    searchEntryCounter.incrementAndGet();
    return PluginResult.IntermediateResponse.continueOperationProcessing(true);
  }



  /**
   * Retrieves the number of times that the search result entry plugins have
   * been called since the last reset.
   *
   * @return  The number of times that the search result entry plugins have been
   *          called since the last reset.
   */
  public static int getSearchEntryCount()
  {
    return searchEntryCounter.get();
  }



  /**
   * Resets the search result entry plugin invocation count to zero.
   *
   * @return  The search result entry plugin invocation count before it was
   *          reset.
   */
  public static int resetSearchEntryCount()
  {
    return searchEntryCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.IntermediateResponse
       processSearchReference(SearchReferenceSearchOperation searchOperation,
                              SearchResultReference searchReference)
  {
    searchReferenceCounter.incrementAndGet();
    return PluginResult.IntermediateResponse.continueOperationProcessing(true);
  }



  /**
   * Retrieves the number of times that the search result reference plugins have
   * been called since the last reset.
   *
   * @return  The number of times that the search result reference plugins have
   *          been called since the last reset.
   */
  public static int getSearchReferenceCount()
  {
    return searchReferenceCounter.get();
  }



  /**
   * Resets the search result reference plugin invocation count to zero.
   *
   * @return  The search result reference plugin invocation count before it was
   *          reset.
   */
  public static int resetSearchReferenceCount()
  {
    return searchReferenceCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.SubordinateModifyDN processSubordinateModifyDN(
              SubordinateModifyDNOperation modifyDNOperation, Entry oldEntry,
              Entry newEntry, List<Modification> modifications)
  {
    subordinateModifyDNCounter.incrementAndGet();
    return PluginResult.SubordinateModifyDN.continueOperationProcessing();
  }




  /**
   * Retrieves the number of times the subordinate modify DN plugins have been
   * called since the last reset.
   *
   * @return  The number of times the subordinate modify DN plugins have been
   *          called since the last reset.
   */
  public static int getSubordinateModifyDNCount()
  {
    return subordinateModifyDNCounter.get();
  }



  /**
   * Resets the subordinate modify DN plugin invocation count to zero.
   *
   * @return  The subordinate modify DN plugin invocation count before it was
   *          reset.
   */
  public static int resetSubordinateModifyDNCount()
  {
    return subordinateModifyDNCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.IntermediateResponse processIntermediateResponse(
              IntermediateResponse intermediateResponse)
  {
    intermediateResponseCounter.incrementAndGet();
    return PluginResult.IntermediateResponse.continueOperationProcessing(true);
  }



  /**
   * Retrieves the number of times the intermediate response plugins have been
   * called since the last reset.
   *
   * @return  The number of times the intermediate response plugins have been
   *          called since the last reset.
   */
  public static int getIntermediateResponseCount()
  {
    return intermediateResponseCounter.get();
  }



  /**
   * Resets the intermediate response plugin invocation count to zero.
   *
   * @return  The intermediate response plugin invocation count before it was
   *          reset.
   */
  public static int resetIntermediateResponseCount()
  {
    return intermediateResponseCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostConnect doPostConnect(ClientConnection
                                                    clientConnection)
  {
    postConnectCounter.incrementAndGet();
    return PluginResult.PostConnect.continueConnectProcessing();
  }



  /**
   * Retrieves the number of times that the post-connect plugins have been
   * called since the last reset.
   *
   * @return  The number of times that the post-connect plugins have been called
   *          since the last reset.
   */
  public static int getPostConnectCount()
  {
    return postConnectCounter.get();
  }



  /**
   * Resets the post-connect plugin invocation count to zero.
   *
   * @return  The post-connect plugin invocation count before it was reset.
   */
  public static int resetPostConnectCount()
  {
    return postConnectCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostDisconnect doPostDisconnect(
          ClientConnection clientConnection, DisconnectReason disconnectReason,
          Message message)
  {
    postDisconnectCounter.incrementAndGet();
    return PluginResult.PostDisconnect.continueDisconnectProcessing();
  }



  /**
   * Retrieves the number of times that the post-disconnect plugins have been
   * called since the last reset.
   *
   * @return  The number of times that the post-disconnect plugins have been
   *          called since the last reset.
   */
  public static int getPostDisconnectCount()
  {
    return postDisconnectCounter.get();
  }



  /**
   * Resets the post-disconnect plugin invocation count to zero.
   *
   * @return  The post-disconnect plugin invocation count before it was reset.
   */
  public static int resetPostDisconnectCount()
  {
    return postDisconnectCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.ImportLDIF doLDIFImport(LDIFImportConfig importConfig,
                                        Entry entry)
  {
    ldifImportCounter.incrementAndGet();
    return PluginResult.ImportLDIF.continueEntryProcessing();
  }



  /**
   * Retrieves the number of times that the LDIF import plugins have been called
   * since the last reset.
   *
   * @return  The number of times that the LDIF import plugins have been called
   *          since the last reset.
   */
  public static int getLDIFImportCount()
  {
    return ldifImportCounter.get();
  }



  /**
   * Resets the LDIF import plugin invocation count to zero.
   *
   * @return  The LDIF import plugin invocation count before it was reset.
   */
  public static int resetLDIFImportCount()
  {
    return ldifImportCounter.getAndSet(0);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.ImportLDIF doLDIFExport(LDIFExportConfig exportConfig,
                                       Entry entry)
  {
    ldifExportCounter.incrementAndGet();
    return PluginResult.ImportLDIF.continueEntryProcessing();
  }



  /**
   * Retrieves the number of times that the LDIF export plugins have been called
   * since the last reset.
   *
   * @return  The number of times that the LDIF export plugins have been called
   *          since the last reset.
   */
  public static int getLDIFExportCount()
  {
    return ldifExportCounter.get();
  }



  /**
   * Resets the LDIF export plugin invocation count to zero.
   *
   * @return  The LDIF export plugin invocation count before it was reset.
   */
  public static int resetLDIFExportCount()
  {
    return ldifExportCounter.getAndSet(0);
  }



  /**
   * Resets all of the invocation counters.  This does not impact the startup
   * or shutdown flag.
   */
  public static void resetAllCounters()
  {
    resetPreParseCount();
    resetPreOperationCount();
    resetPostOperationCount();
    resetPostResponseCount();
    resetPostSynchronizationCount();
    resetSearchEntryCount();
    resetSearchReferenceCount();
    resetSubordinateModifyDNCount();
    resetIntermediateResponseCount();
    resetPostConnectCount();
    resetPostDisconnectCount();
    resetLDIFImportCount();
    resetLDIFExportCount();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.Startup doStartup()
  {
    startupCalled = true;
    return PluginResult.Startup.continueStartup();
  }



  /**
   * Indicates whether the server startup plugins have been called.
   *
   * @return  <CODE>true</CODE> if the server startup plugins have been called,
   *          or <CODE>false</CODE> if not.
   */
  public static boolean startupCalled()
  {
    return startupCalled;
  }



  /**
   * Resets the flag that indicates whether the startup plugins have been
   * called.
   */
  public static void resetStartupCalled()
  {
    startupCalled = false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void doShutdown(Message reason)
  {
    shutdownCalled = true;
  }



  /**
   * Indicates whether the server shutdown plugins have been called.
   *
   * @return  <CODE>true</CODE> if the server shutdown plugins have been called,
   *          or <CODE>false</CODE> if not.
   */
  public static boolean shutdownCalled()
  {
    return shutdownCalled;
  }



  /**
   * Resets the flag that indicates whether the shutdown plugins have been
   * called.
   */
  public static void resetShutdownCalled()
  {
    shutdownCalled = false;
  }



  /**
   * Waits up to five seconds until the post-response plugins have been called
   * at least once since the last reset.
   * @return The number of times that the post-response plugins have been
   *         called since the last reset.  The return value may be zero if the
   *         wait timed out.
   * @throws InterruptedException If another thread interrupts this thread.
   */
  public static int waitForPostResponse() throws InterruptedException
  {
    long timeout = System.currentTimeMillis() + 5000;
    while (postResponseCounter.get() == 0 &&
         System.currentTimeMillis() < timeout)
    {
      Thread.sleep(10);
    }
    return postResponseCounter.get();
  }
}

