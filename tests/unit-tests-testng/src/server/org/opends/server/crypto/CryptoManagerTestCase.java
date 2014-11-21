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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.crypto;

import static org.testng.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Mac;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.*;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 This class tests the CryptoManager.
 */
@SuppressWarnings("javadoc")
public class CryptoManagerTestCase extends CryptoTestCase {

  @BeforeClass()
  public void setUp()
         throws Exception {
    TestCaseUtils.startServer();
  }

  @AfterClass()
  public void CleanUp() throws Exception {
    // Removes at least secret keys added in this test case.
    TestCaseUtils.restartServer();
  }


  @Test
  public void testGetInstanceKeyCertificate()
          throws Exception {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final byte[] cert
            = CryptoManagerImpl.getInstanceKeyCertificateFromLocalTruststore();
    assertNotNull(cert);

    // The certificate should now be accessible in the truststore backend via LDAP.
    final InitialLdapContext ctx = ConnectionUtils.createLdapsContext(
            "ldaps://" + "127.0.0.1" + ":"
                    + String.valueOf(TestCaseUtils.getServerAdminPort()),
            "cn=Directory Manager", "password",
          ConnectionUtils.getDefaultLDAPTimeout(), null, null, null);
    // TODO: should the below dn be in ConfigConstants?
    final String dnStr = "ds-cfg-key-id=ads-certificate,cn=ads-truststore";
    final LdapName dn = new LdapName(dnStr);
    final SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    final String attrIDs[] = { "ds-cfg-public-key-certificate;binary" };
    searchControls.setReturningAttributes(attrIDs);
    final SearchResult certEntry = ctx.search(dn,
               "(objectclass=ds-cfg-instance-key)", searchControls).next();
    final javax.naming.directory.Attribute certAttr
            = certEntry.getAttributes().get(attrIDs[0]);
    /* attribute ds-cfg-public-key-certificate is a MUST in the schema */
    assertNotNull(certAttr);
    byte[] ldapCert = (byte[])certAttr.get();
    // Compare the certificate values.
    assertTrue(Arrays.equals(ldapCert, cert));

    // Compare the MD5 hash of the LDAP attribute with the one
    // retrieved from the CryptoManager.
    MessageDigest md = MessageDigest.getInstance("MD5");
    assertTrue(StaticUtils.bytesToHexNoSpace(
         md.digest(ldapCert)).equals(cm.getInstanceKeyID()));

    // Call twice to ensure idempotent.
    CryptoManagerImpl.publishInstanceKeyEntryInADS();
    CryptoManagerImpl.publishInstanceKeyEntryInADS();
  }

  @Test
  public void testMacSuccess()
          throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String text = "1234";

    final String macKeyID = cm.getMacEngineKeyEntryID();

    final Mac signingMac = cm.getMacEngine(macKeyID);
    final byte[] signedHash = signingMac.doFinal(text.getBytes());

    final Mac validatingMac = cm.getMacEngine(macKeyID);
    final byte[] calculatedSignature = validatingMac.doFinal(text.getBytes());

    assertTrue(Arrays.equals(calculatedSignature, signedHash));
  }

  // TODO: other-than-default MAC

  /**
   Cipher parameters
   */
  private class CipherParameters {
    private final String fAlgorithm;
    private final String fMode;
    private final String fPadding;
    private final int fKeyLength;
    private final int fIVLength;

    public CipherParameters(final String algorithm, final String mode,
                            final String padding, final int keyLength,
                            final int ivLength) {
      fAlgorithm = algorithm;
      fMode = mode;
      fPadding = padding;
      fKeyLength = keyLength;
      fIVLength = ivLength;
    }

    public String getTransformation() {
      if (null == fAlgorithm) return null; // default
      return (null == fMode)
              ? fAlgorithm
              : (new StringBuilder(fAlgorithm)).append("/").append(fMode)
                .append("/").append(fPadding).toString();
    }

    public int getKeyLength() {
      return fKeyLength;
    }

    public int getIVLength() {
      return fIVLength;
    }
  }


  /**
   Cipher parameter data set.

   @return The set of Cipher parameters with which to test.
   */
  @DataProvider(name = "cipherParametersData")
  public Object[][] cipherParametersData() {

    List<CipherParameters> paramList = new LinkedList<CipherParameters>();
    // default (preferred) AES/CBC/PKCS5Padding 128bit key.
    paramList.add(new CipherParameters(null, null, null, 128, 128));
    // custom
// TODO: https://opends.dev.java.net/issues/show_bug.cgi?id=2448
// TODO: paramList.add(new CipherParameters("Blowfish", "CFB", "NoPadding", 448, 64));
// TODO: paramList.add(new CipherParameters("AES", "CBC", "PKCS5Padding", 256, 64));
    paramList.add(new CipherParameters("AES", "CFB", "NoPadding", 128, 64));
    paramList.add(new CipherParameters("Blowfish", "CFB", "NoPadding", 128, 64));
    paramList.add(new CipherParameters("RC4", null, null, 104, 0));
    paramList.add(new CipherParameters("RC4", "NONE", "NoPadding", 128, 0));
    paramList.add(new CipherParameters("DES", "CFB", "NoPadding", 56, 64));
    paramList.add(new CipherParameters("DESede", "ECB", "PKCS5Padding", 168, 64));

    Object[][] cipherParameters = new Object[paramList.size()][1];
    for (int i=0; i < paramList.size(); i++)
    {
      cipherParameters[i] = new Object[] { paramList.get(i) };
    }

    return cipherParameters;
  }


  /**
   Tests a simple encryption-decryption cycle using the supplied cipher
   parameters.

   @param cp  Cipher parameters to use for this test iteration.

   @throws Exception If an exceptional condition arises.
   */
@Test(dataProvider="cipherParametersData")
  public void testEncryptDecryptSuccess(CipherParameters cp)
          throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = (null == cp.getTransformation())
            ? cm.encrypt(secretMessage.getBytes()) // default
            : cm.encrypt(cp.getTransformation(), cp.getKeyLength(),
                         secretMessage.getBytes());
    assertEquals(-1, new String(cipherText).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);
  }


  /**
   Tests a simple cipher stream encryption-decryption cycle using the supplied
   cipher parameters.

   @param cp  Cipher parameters to use for this test iteration.

   @throws Exception If an exceptional condition arises.
   */
  @Test(dataProvider="cipherParametersData")
  public void testStreamEncryptDecryptSuccess(CipherParameters cp)
          throws Exception {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = (null == cp.getTransformation())
            ? cm.getCipherOutputStream(os) // default
            : cm.getCipherOutputStream(cp.getTransformation(), cp.getKeyLength(), os);
    os.write(secretMessage.getBytes());
    os.close();

    // TODO: check tempfile for plaintext.

    InputStream is = new FileInputStream(tempFile);
    is = cm.getCipherInputStream(is);
    byte[] plainText = new byte[secretMessage.getBytes().length];
    assertEquals(is.read(plainText), secretMessage.getBytes().length);
    assertEquals(is.read(), -1);
    is.close();
    assertEquals(new String(plainText), secretMessage);
  }

  /**
   Tests to ensure the same key identifier (and hence, key) is used for
   successive encryptions specifying the same algorithm and key length.
   <p>
   The default encryption cipher requires an initialization vector. Confirm
   successive uses of a key produces distinct ciphertext.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test
  public void testKeyEntryReuse()
          throws Exception {

    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";

    final byte[] cipherText = cm.encrypt(secretMessage.getBytes());
    final byte[] cipherText2 = cm.encrypt(secretMessage.getBytes());

    // test cycle
    final byte[] plainText = cm.decrypt(cipherText2);
    assertEquals(new String(plainText), secretMessage);

    // test for identical keys
    final byte[] keyID = Arrays.copyOfRange(cipherText, 1, 16);
    final byte[] keyID2 = Arrays.copyOfRange(cipherText2, 1, 16);
    assertTrue(Arrays.equals(keyID, keyID2));

    // test for distinct ciphertext
    assertTrue(! Arrays.equals(cipherText, cipherText2));
  }


  /**
   Test that secret keys are persisted: Encrypt some data using a
   variety of transformations, restart the instance, and decrypt the
   retained ciphertext.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test()
  public void testKeyPersistence()
        throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";

    final byte[] cipherText = cm.encrypt("Blowfish/CFB/NoPadding", 128,
            secretMessage.getBytes());
    final byte[] cipherText2 = cm.encrypt("RC4", 104,
            secretMessage.getBytes());

    EmbeddedUtils.restartServer(
            this.getClass().getName(),
            Message.raw("CryptoManager: testing persistent secret keys."),
            DirectoryServer.getEnvironmentConfig());

    byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);
    plainText = cm.decrypt(cipherText2);
    assertEquals(new String(plainText), secretMessage);
  }


  /**
   Mark a key compromised; ensure 1) subsequent encryption requests use a
   new key; 2) ciphertext produced using the compromised key can still be
   decrypted; 3) once the compromised key entry is removed, confirm ciphertext
   produced using the compromised key can no longer be decrypted.

   @throws Exception In case something exceptional happens.
   */
  @Test()
  public void testCompromisedKey() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";
    final String cipherTransformationName = "AES/CBC/PKCS5Padding";
    final int cipherKeyLength = 128;

    // Initial encryption ensures a cipher key entry is in ADS.
    final byte[] cipherText = cm.encrypt(cipherTransformationName,
            cipherKeyLength, secretMessage.getBytes());

    // Retrieve all uncompromised cipher key entries corresponding to the
    // specified transformation and key length. Mark each entry compromised.
    final String baseDNStr // TODO: is this DN defined elsewhere as a constant?
            = "cn=secret keys," + ADSContext.getAdministrationSuffixDN();
    final DN baseDN = DN.decode(baseDNStr);
    final String FILTER_OC_INSTANCE_KEY
            = new StringBuilder("(objectclass=")
            .append(ConfigConstants.OC_CRYPTO_CIPHER_KEY)
            .append(")").toString();
    final String FILTER_NOT_COMPROMISED = new StringBuilder("(!(")
            .append(ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME)
            .append("=*))").toString();
    final String FILTER_CIPHER_TRANSFORMATION_NAME = new StringBuilder("(")
            .append(ConfigConstants.ATTR_CRYPTO_CIPHER_TRANSFORMATION_NAME)
            .append("=").append(cipherTransformationName)
            .append(")").toString();
    final String FILTER_CIPHER_KEY_LENGTH = new StringBuilder("(")
            .append(ConfigConstants.ATTR_CRYPTO_KEY_LENGTH_BITS)
            .append("=").append(String.valueOf(cipherKeyLength))
            .append(")").toString();
    final String searchFilter = new StringBuilder("(&")
            .append(FILTER_OC_INSTANCE_KEY)
            .append(FILTER_NOT_COMPROMISED)
            .append(FILTER_CIPHER_TRANSFORMATION_NAME)
            .append(FILTER_CIPHER_KEY_LENGTH)
            .append(")").toString();
    final LinkedHashSet<String> requestedAttributes
            = new LinkedHashSet<String>();
    requestedAttributes.add("dn");
    final InternalClientConnection icc
            = InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOp = icc.processSearch(
            baseDN,
            SearchScope.SINGLE_LEVEL,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            /* size limit */ 0, /* time limit */ 0,
            /* types only */ false,
            SearchFilter.createFilterFromString(searchFilter),
            requestedAttributes);
    assertTrue(0 < searchOp.getSearchEntries().size());

    String compromisedTime = TimeThread.getGeneralizedTime();
    for (Entry e : searchOp.getSearchEntries()) {
      TestCaseUtils.applyModifications(true,
        "dn: " + e.getDN().toNormalizedString(),
        "changetype: modify",
        "replace: " + ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME,
        ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME + ": "
                + compromisedTime);
    }
    //Wait so the above asynchronous modification can be applied. The crypto
    //manager's cipherKeyEntryCache needs to be updated before the encrypt()
    //method is called below.
    Thread.sleep(1000);
    // Use the transformation and key length again. A new cipher key
    // should be produced.
    final byte[] cipherText2 = cm.encrypt(cipherTransformationName,
            cipherKeyLength, secretMessage.getBytes());

    // 1. Test for distinct keys.
    final byte[] keyID = new byte[16];
    final byte[] keyID2 = new byte[16];
    System.arraycopy(cipherText, 1, keyID, 0, 16);
    System.arraycopy(cipherText2, 1, keyID2, 0, 16);
    assertTrue(! Arrays.equals(keyID, keyID2));

    // 2. Confirm ciphertext produced using the compromised key can still be
    // decrypted.
    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);

    // 3. Delete the compromised entry(ies) and ensure ciphertext produced
    // using a compromised key can no longer be decrypted.
    for (Entry e : searchOp.getSearchEntries()) {
      TestCaseUtils.applyModifications(true,
        "dn: " + e.getDN().toNormalizedString(), "changetype: delete");
    }
    Thread.sleep(1000); // Clearing the cache is asynchronous.
    try {
      cm.decrypt(cipherText);
    }
    catch (CryptoManagerException ex) {
      // TODO: if reasons are added to CryptoManagerException, check for
      // expected cause.
    }
  }

  /**
   TODO: Test shared secret key wrapping (various wrapping ciphers, if configurable).
   */


  /**
   TODO: Test the secret key synchronization protocol.

     1. Create the first instance; add reversible password storage scheme
     to password policy; add entry using explicit password policy; confirm
     secret key entry has been produced.

     2. Create and initialize the second instance into the existing ADS domain.
     The secret key entries should be propagated to the second instance via
     replication. Then the new instance should detect that the secret key
     entries are missing ds-cfg-symmetric-key attribute values for that
     instance, inducing the key synchronization protocol.

     3. Confirm the second instance can decrypt the password of the entry
     added in step 1; e.g., bind as that user.

     4. Stop the second instance. At the first instance, enable a different
     reversible password storage scheme (different cipher transformation,
     and hence secret key entry); add another entry using that password
     storage scheme; start the second instance; ensure the password can
     be decrypted at the second instance.
     */
}
