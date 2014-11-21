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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012 ForgeRock AS
 */
package org.opends.server.extensions;



import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.security.auth.x500.X500Principal;
import static org.opends.messages.ExtensionMessages.*;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CertificateMapperCfg;
import org.opends.server.admin.std.server.FingerprintCertificateMapperCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.CertificateMapper;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.*;
import static org.opends.server.util.StaticUtils.bytesToColonDelimitedHex;
import static org.opends.server.util.StaticUtils.getExceptionMessage;



/**
 * This class implements a very simple Directory Server certificate mapper that
 * will map a certificate to a user only if that user's entry contains an
 * attribute with the fingerprint of the client certificate.  There must be
 * exactly one matching user entry for the mapping to be successful.
 */
public class FingerprintCertificateMapper
       extends CertificateMapper<FingerprintCertificateMapperCfg>
       implements ConfigurationChangeListener<
                       FingerprintCertificateMapperCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The DN of the configuration entry for this certificate mapper.
  private DN configEntryDN;

  // The current configuration for this certificate mapper.
  private FingerprintCertificateMapperCfg currentConfig;

  // The algorithm that will be used to generate the fingerprint.
  private String fingerprintAlgorithm;

  // The set of attributes to return in search result entries.
  private LinkedHashSet<String> requestedAttributes;


  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public FingerprintCertificateMapper()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeCertificateMapper(
                   FingerprintCertificateMapperCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addFingerprintChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();


    // Get the algorithm that will be used to generate the fingerprint.
    switch (configuration.getFingerprintAlgorithm())
    {
      case MD5:
        fingerprintAlgorithm = "MD5";
        break;
      case SHA1:
        fingerprintAlgorithm = "SHA1";
        break;
    }


    // Make sure that the fingerprint attribute is configured for equality in
    // all appropriate backends.
    Set<DN> cfgBaseDNs = configuration.getUserBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    AttributeType t = configuration.getFingerprintAttribute();
    for (DN baseDN : cfgBaseDNs)
    {
      Backend b = DirectoryServer.getBackend(baseDN);
      if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
      {
        Message message = WARN_SATUACM_ATTR_UNINDEXED.get(
            configuration.dn().toString(),
            t.getNameOrOID(), b.getBackendID());
        ErrorLogger.logError(message);
      }
    }

    // Create the attribute list to include in search requests.  We want to
    // include all user and operational attributes.
    requestedAttributes = new LinkedHashSet<String>(2);
    requestedAttributes.add("*");
    requestedAttributes.add("+");
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeCertificateMapper()
  {
    currentConfig.removeFingerprintChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    FingerprintCertificateMapperCfg config = currentConfig;
    AttributeType fingerprintAttributeType = config.getFingerprintAttribute();
    String theFingerprintAlgorithm = this.fingerprintAlgorithm;

    // Make sure that a peer certificate was provided.
    if ((certificateChain == null) || (certificateChain.length == 0))
    {
      Message message = ERR_FCM_NO_PEER_CERTIFICATE.get();
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the first certificate in the chain.  It must be an X.509 certificate.
    X509Certificate peerCertificate;
    try
    {
      peerCertificate = (X509Certificate) certificateChain[0];
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_FCM_PEER_CERT_NOT_X509.get(
          String.valueOf(certificateChain[0].getType()));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the signature from the peer certificate and create a digest of it
    // using the configured algorithm.
    String fingerprintString;
    try
    {
      MessageDigest digest = MessageDigest.getInstance(theFingerprintAlgorithm);
      byte[] fingerprintBytes = digest.digest(peerCertificate.getEncoded());
      fingerprintString = bytesToColonDelimitedHex(fingerprintBytes);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      String peerSubject = peerCertificate.getSubjectX500Principal().getName(
                                X500Principal.RFC2253);

      Message message = ERR_FCM_CANNOT_CALCULATE_FINGERPRINT.get(
          peerSubject, getExceptionMessage(e));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Create the search filter from the fingerprint.
    AttributeValue value =
        AttributeValues.create(fingerprintAttributeType, fingerprintString);
    SearchFilter filter =
         SearchFilter.createEqualityFilter(fingerprintAttributeType, value);


    // If we have an explicit set of base DNs, then use it.  Otherwise, use the
    // set of public naming contexts in the server.
    Collection<DN> baseDNs = config.getUserBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }


    // For each base DN, issue an internal search in an attempt to map the
    // certificate.
    Entry userEntry = null;
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    for (DN baseDN : baseDNs)
    {
      InternalSearchOperation searchOperation =
           conn.processSearch(baseDN, SearchScope.WHOLE_SUBTREE,
                              DereferencePolicy.NEVER_DEREF_ALIASES, 1, 10,
                              false, filter, requestedAttributes);

      switch (searchOperation.getResultCode())
      {
        case SUCCESS:
          // This is fine.  No action needed.
          break;

        case NO_SUCH_OBJECT:
          // The search base doesn't exist.  Not an ideal situation, but we'll
          // ignore it.
          break;

        case SIZE_LIMIT_EXCEEDED:
          // Multiple entries matched the filter.  This is not acceptable.
          Message message = ERR_FCM_MULTIPLE_SEARCH_MATCHING_ENTRIES.get(
                        fingerprintString);
          throw new DirectoryException(
                  ResultCode.INVALID_CREDENTIALS, message);


        case TIME_LIMIT_EXCEEDED:
        case ADMIN_LIMIT_EXCEEDED:
          // The search criteria was too inefficient.
          message = ERR_FCM_INEFFICIENT_SEARCH.get(
                         fingerprintString,
                         String.valueOf(searchOperation.getErrorMessage()));
          throw new DirectoryException(searchOperation.getResultCode(),
              message);

        default:
          // Just pass on the failure that was returned for this search.
          message = ERR_FCM_SEARCH_FAILED.get(
                         fingerprintString,
                         String.valueOf(searchOperation.getErrorMessage()));
          throw new DirectoryException(searchOperation.getResultCode(),
              message);
      }

      for (SearchResultEntry entry : searchOperation.getSearchEntries())
      {
        if (userEntry == null)
        {
          userEntry = entry;
        }
        else
        {
          Message message = ERR_FCM_MULTIPLE_MATCHING_ENTRIES.
              get(fingerprintString, String.valueOf(userEntry.getDN()),
                  String.valueOf(entry.getDN()));
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
        }
      }
    }


    // If we've gotten here, then we either found exactly one user entry or we
    // didn't find any.  Either way, return the entry or null to the caller.
    return userEntry;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(CertificateMapperCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    FingerprintCertificateMapperCfg config =
         (FingerprintCertificateMapperCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      FingerprintCertificateMapperCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
              FingerprintCertificateMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the algorithm that will be used to generate the fingerprint.
    String newFingerprintAlgorithm = null;
    switch (configuration.getFingerprintAlgorithm())
    {
      case MD5:
        newFingerprintAlgorithm = "MD5";
        break;
      case SHA1:
        newFingerprintAlgorithm = "SHA1";
        break;
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      fingerprintAlgorithm = newFingerprintAlgorithm;
      currentConfig        = configuration;
    }

    // Make sure that the fingerprint attribute is configured for equality in
    // all appropriate backends.
    Set<DN> cfgBaseDNs = configuration.getUserBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    AttributeType t = configuration.getFingerprintAttribute();
    for (DN baseDN : cfgBaseDNs)
    {
      Backend b = DirectoryServer.getBackend(baseDN);
      if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
      {
        Message message = WARN_SATUACM_ATTR_UNINDEXED.get(
            configuration.dn().toString(),
            t.getNameOrOID(), b.getBackendID());
        messages.add(message);
        ErrorLogger.logError(message);
      }
    }

   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

