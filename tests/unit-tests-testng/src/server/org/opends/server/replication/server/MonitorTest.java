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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.testng.Assert.*;

/**
 * Tests for the replicationServer code.
 */

public class MonitorTest extends ReplicationTestCase
{
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private static final String baseDnStr = TEST_ROOT_DN_STRING;
  private static final String testName = "monitorTest";

  private static final int   WINDOW_SIZE = 10;
  private static final int server1ID = 1;
  private static final int server2ID = 2;
  private static final int server3ID = 3;
  private static final int server4ID = 4;
  private static final int changelog1ID = 21;
  private static final int changelog2ID = 22;
  private static final int changelog3ID = 23;

  private DN baseDn;
  private ReplicationBroker broker2 = null;
  private ReplicationBroker broker3 = null;
  private ReplicationBroker broker4 = null;
  private ReplicationServer replServer1 = null;
  private ReplicationServer replServer2 = null;
  private ReplicationServer replServer3 = null;
  LDAPReplicationDomain replDomain = null;
  protected String[] updatedEntries;

  private static int[] replServerPort = new int[30];

  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST **" + s);
    }
  }
  protected void debugInfo(String message, Exception e)
  {
    debugInfo(message + stackTraceToSingleLineString(e));
  }

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    baseDn = DN.decode(baseDnStr);

    updatedEntries = newLDIFEntries();
  }

  /*
   * Creates entries necessary to the test.
   */
  private String[] newLDIFEntries()
  {

    return new String[]{
        "dn: " + baseDn + "\n"
            + "objectClass: top\n"
            + "objectClass: organization\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111111\n"
            + "\n",
        "dn: ou=People," + baseDn + "\n"
            + "objectClass: top\n"
            + "objectClass: organizationalUnit\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111112\n"
            + "\n",
        "dn: cn=Fiona Jensen,ou=people," + baseDn + "\n"
            + "objectclass: top\n"
            + "objectclass: person\n"
            + "objectclass: organizationalPerson\n"
            + "objectclass: inetOrgPerson\n"
            + "cn: Fiona Jensen\n"
            + "sn: Jensen\n"
            + "uid: fiona\n"
            + "telephonenumber: +1 408 555 1212\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111113\n"
            + "\n",
        "dn: cn=Robert Langman,ou=people," + baseDn + "\n"
            + "objectclass: top\n"
            + "objectclass: person\n"
            + "objectclass: organizationalPerson\n"
            + "objectclass: inetOrgPerson\n"
            + "cn: Robert Langman\n"
            + "sn: Langman\n"
            + "uid: robert\n"
            + "telephonenumber: +1 408 555 1213\n"
            + "entryUUID: 21111111-1111-1111-1111-111111111114\n"
            + "\n"
    };
  }

  /**
   * Creates a new replicationServer.
   * @param changelogId The serverID of the replicationServer to create.
   * @param all         Specifies whether to connect the created replication
   *                    server to the other replication servers in the test.
   * @return The new created replication server.
   */
  private ReplicationServer createReplicationServer(int changelogId,
      boolean all, String suffix)
  {
    SortedSet<String> servers = new TreeSet<String>();
    try
    {
      if (all)
      {
        if (changelogId != changelog1ID)
          servers.add("localhost:" + getChangelogPort(changelog1ID));
        if (changelogId != changelog2ID)
          servers.add("localhost:" + getChangelogPort(changelog2ID));
      }
      int chPort = getChangelogPort(changelogId);
      String chDir = "monitorTest"+changelogId+suffix+"Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(chPort, chDir, 0, changelogId, 0, 100,
            servers);
      ReplicationServer replicationServer = new ReplicationServer(conf);
      Thread.sleep(1000);

      return replicationServer;
    }
    catch (Exception e)
    {
      fail("createChangelog" + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Create a synchronized suffix in the current server providing the
   * replication Server ID.
   * @param changelogID the replication server ID.
   */
  private void connectServer1ToChangelog(int changelogID)
  {
    // Connect DS to the replicationServer
    try
    {
      // suffix synchronized
      String synchroServerLdif =
        "dn: cn=" + testName + ", cn=domains," + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + testName + "\n"
        + "ds-cfg-base-dn: " + baseDnStr + "\n"
        + "ds-cfg-replication-server: localhost:"
        + getChangelogPort(changelogID)+"\n"
        + "ds-cfg-server-id: " + server1ID + "\n"
        + "ds-cfg-receive-status: true\n"
        + "ds-cfg-window-size: " + WINDOW_SIZE;

      synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
      DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
      configEntryList.add(synchroServerEntry.getDN());

      replDomain = LDAPReplicationDomain.retrievesReplicationDomain(baseDn);

      if (replDomain != null)
      {
        debugInfo("ReplicationDomain: Import/Export is running ? " +
          replDomain.ieRunning());
      }
    }
    catch(Exception e)
    {
      debugInfo("connectToReplServer", e);
      fail("connectToReplServer: " + e.getMessage() + " : " + e.getStackTrace(), e);
    }
  }

  /*
   * Disconnect DS from the replicationServer
   */
  private void disconnectFromReplServer(int changelogID)
  {
    try
    {
      // suffix synchronized
      String synchroServerStringDN = "cn=" + testName + ", cn=domains," +
      SYNCHRO_PLUGIN_DN;
      // Must have called connectServer1ToChangelog previously
      assertTrue(synchroServerEntry != null);
      DN synchroServerDN = DN.decode(synchroServerStringDN);
      deleteEntry(synchroServerDN);
      synchroServerEntry = null;
      configEntryList.remove(configEntryList.indexOf(synchroServerDN));

    }
    catch(Exception e)
    {
      fail("disconnectFromReplServer", e);
    }
  }

  private int getChangelogPort(int changelogID)
  {
    if (replServerPort[changelogID] == 0)
    {
      try
      {
        // Find  a free port for the replicationServer
        ServerSocket socket = TestCaseUtils.bindFreePort();
        replServerPort[changelogID] = socket.getLocalPort();
        socket.close();
      }
      catch(Exception e)
      {
        fail("Cannot retrieve a free port for replication server."
            + e.getMessage());
      }
    }
    return replServerPort[changelogID];
  }

  private String createEntry(UUID uid)
  {
    String user2dn = "uid=user"+uid+",ou=People," + baseDnStr;
    return "dn: " + user2dn + "\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "homePhone: 951-245-7634\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "mobile: 027-085-0537\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar2\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
        + "street: 17984 Thirteenth Street\n"
        + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 2\n"
        + "sn: Amar2\n" + "givenName: Aaccf2\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
  }

  static protected ReplicationMsg createAddMsg(ChangeNumber cn,
      int serverId)
  {
    Entry personWithUUIDEntry = null;
    String user1entryUUID;
    String baseUUID = null;
    String user1dn;

    user1entryUUID = "33333333-3333-3333-3333-333333333333";
    user1dn = "uid=user1,ou=People," + baseDnStr;
    String entryWithUUIDldif = "dn: "+ user1dn + "\n"
    + "objectClass: top\n" + "objectClass: person\n"
    + "objectClass: organizationalPerson\n"
    + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
    + "homePhone: 951-245-7634\n"
    + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
    + "mobile: 027-085-0537\n"
    + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
    + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
    + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
    + "street: 17984 Thirteenth Street\n"
    + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
    + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
    + "userPassword: password\n" + "initials: AA\n"
    + "entryUUID: " + user1entryUUID + "\n";

    try
    {
      personWithUUIDEntry = TestCaseUtils.entryFromLdifString(entryWithUUIDldif);
    }
    catch(Exception e)
    {
      fail(e.getMessage());
    }

    // Create and publish an update message to add an entry.
    return new AddMsg(cn,
        personWithUUIDEntry.getDN().toString(),
        user1entryUUID,
        baseUUID,
        personWithUUIDEntry.getObjectClassAttribute(),
        personWithUUIDEntry.getAttributes(), new ArrayList<Attribute>());
  }

  @Test(enabled=true)
  public void testMultiRS() throws Exception
  {
    String testCase = "testMultiRS";
    debugInfo("Starting " + testCase);

    try
    {
      TestCaseUtils.initializeTestBackend(false);

      debugInfo("Creating 2 RS");
      replServer1 = createReplicationServer(changelog1ID, true, testCase);
      replServer2 = createReplicationServer(changelog2ID, true, testCase);
      replServer3 = createReplicationServer(changelog3ID, true, testCase);
      Thread.sleep(500);

      debugInfo("Connecting DS to replServer1");
      connectServer1ToChangelog(changelog1ID);

      boolean emptyOldChanges = true;
      try
      {
        debugInfo("Connecting broker2 to replServer1");
        broker2 = openReplicationSession(baseDn,
          server2ID, 100, getChangelogPort(changelog1ID),
          1000, !emptyOldChanges);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      try
      {
        debugInfo("Connecting broker3 to replServer2");
        broker3 = openReplicationSession(baseDn,
          server3ID, 100, getChangelogPort(changelog2ID),
          1000, !emptyOldChanges);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      try
      {
        debugInfo("Connecting broker4 to replServer2");
        broker4 = openReplicationSession(baseDn,
          server4ID, 100, getChangelogPort(changelog2ID),
          1000, !emptyOldChanges);
        Thread.sleep(1000);
      } catch (SocketException se)
      {
        fail("Broker connection is expected to be accepted.");
      }

      // Do a bunch of change
      updatedEntries = newLDIFEntries();
      this.addTestEntriesToDB(updatedEntries);

      for (int i = 0; i < 200; i++)
      {
        String ent1[] =
        {
          createEntry(UUID.randomUUID())
        };
        this.addTestEntriesToDB(ent1);
      }

      /*
       * Create a Change number generator to generate new changenumbers
       * when we need to send operation messages to the replicationServer.
       */
      ChangeNumberGenerator gen = new ChangeNumberGenerator(server3ID, 0);

      for (int i = 0; i < 10; i++)
      {
        broker3.publish(createAddMsg(gen.newChangeNumber(), server3ID));
      }

      searchMonitor();

      debugInfo(
        "Disconnect DS from replServer1 (required in order to DEL entries).");
      disconnectFromReplServer(changelog1ID);


      debugInfo("Successfully ending " + testCase);
    } finally
    {
      debugInfo("Cleaning entries");
      postTest();
    }
  }

  /**
   * Disconnect broker and remove entries from the local DB
   */
  protected void postTest()
  {
    debugInfo("Post test cleaning.");

    // Clean brokers
    if (broker2 != null)
      broker2.stop();
    broker2 = null;
    if (broker3 != null)
      broker3.stop();
    broker3 = null;
    if (broker4 != null)
      broker4.stop();
    broker4 = null;

    if (replServer1 != null)
    {
      replServer1.clearDb();
      replServer1.remove();
      StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer1.getDbDirName()));
      replServer1 = null;
    }
    if (replServer2 != null)
    {
      replServer2.clearDb();
      replServer2.remove();
      StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer2.getDbDirName()));
      replServer2 = null;
    }
    if (replServer3 != null)
    {
      replServer3.clearDb();
      replServer3.remove();
      StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replServer3.getDbDirName()));
     replServer3 = null;
    }

    super.cleanRealEntries();

    // Clean replication server ports
    for (int i = 0; i < replServerPort.length; i++)
    {
      replServerPort[i] = 0;
    }

    try
    {
      TestCaseUtils.initializeTestBackend(false);
    }
    catch (Exception e) {}
  }

  private static final ByteArrayOutputStream oStream =
    new ByteArrayOutputStream();
  private static final ByteArrayOutputStream eStream =
    new ByteArrayOutputStream();

  private void searchMonitor()
  {
    // test search as directory manager returns content
    String[] args3 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "cn=monitor",
      "-s", "sub",
      "(domain-name=*)"
    };

    oStream.reset();
    eStream.reset();
    int retVal =
      LDAPSearch.mainSearch(args3, false, oStream, eStream);
    String entries = oStream.toString();
    debugInfo("Entries:" + entries);
    try
    {
      assertEquals(retVal, 0, "Returned error: " + eStream);
      assertTrue(!entries.equalsIgnoreCase(""), "Returned entries: " + entries);
    }
    catch(Exception e)
    {
      if (debugEnabled())
        TRACER.debugInfo(
          stackTraceToSingleLineString(new Exception()));
      fail(e.getMessage());
    }
  }
}
