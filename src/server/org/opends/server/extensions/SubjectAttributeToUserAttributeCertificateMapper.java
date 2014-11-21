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
 *      Portions Copyright 2013 Manuel Gaupp
 */
package org.opends.server.extensions;



import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.security.auth.x500.X500Principal;
import static org.opends.messages.ExtensionMessages.*;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CertificateMapperCfg;
import org.opends.server.admin.std.server
    .SubjectAttributeToUserAttributeCertificateMapperCfg;
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
import static org.opends.server.util.StaticUtils.toLowerCase;



/**
 * This class implements a very simple Directory Server certificate mapper that
 * will map a certificate to a user based on attributes contained in both the
 * certificate subject and the user's entry.  The configuration may include
 * mappings from certificate attributes to attributes in user entries, and all
 * of those certificate attributes that are present in the subject will be used
 * to search for matching user entries.
 */
public class SubjectAttributeToUserAttributeCertificateMapper
       extends CertificateMapper<
               SubjectAttributeToUserAttributeCertificateMapperCfg>
       implements ConfigurationChangeListener<
                  SubjectAttributeToUserAttributeCertificateMapperCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The DN of the configuration entry for this certificate mapper.
  private DN configEntryDN;

  // The mappings between certificate attribute names and user attribute types.
  private LinkedHashMap<String,AttributeType> attributeMap;

  // The current configuration for this certificate mapper.
  private SubjectAttributeToUserAttributeCertificateMapperCfg currentConfig;

  // The set of attributes to return in search result entries.
  private LinkedHashSet<String> requestedAttributes;


  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public SubjectAttributeToUserAttributeCertificateMapper()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeCertificateMapper(
                   SubjectAttributeToUserAttributeCertificateMapperCfg
                        configuration)
         throws ConfigException, InitializationException
  {
    configuration
        .addSubjectAttributeToUserAttributeChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();

    // Get and validate the subject attribute to user attribute mappings.
    attributeMap = new LinkedHashMap<String,AttributeType>();
    for (String mapStr : configuration.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        Message message = ERR_SATUACM_INVALID_MAP_FORMAT.get(
            String.valueOf(configEntryDN), mapStr);
        throw new ConfigException(message);
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if ((certAttrName.length() == 0) || (userAttrName.length() == 0))
      {
        Message message = ERR_SATUACM_INVALID_MAP_FORMAT.get(
            String.valueOf(configEntryDN), mapStr);
        throw new ConfigException(message);
      }

      // Try to normalize the provided certAttrName
      certAttrName = normalizeAttributeName(certAttrName);


      if (attributeMap.containsKey(certAttrName))
      {
        Message message = ERR_SATUACM_DUPLICATE_CERT_ATTR.get(
            String.valueOf(configEntryDN), certAttrName);
        throw new ConfigException(message);
      }

      AttributeType userAttrType =
           DirectoryServer.getAttributeType(userAttrName, false);
      if (userAttrType == null)
      {
        Message message = ERR_SATUACM_NO_SUCH_ATTR.get(
            mapStr, String.valueOf(configEntryDN), userAttrName);
        throw new ConfigException(message);
      }

      for (AttributeType attrType : attributeMap.values())
      {
        if (attrType.equals(userAttrType))
        {
          Message message = ERR_SATUACM_DUPLICATE_USER_ATTR.get(
              String.valueOf(configEntryDN), attrType.getNameOrOID());
          throw new ConfigException(message);
        }
      }

      attributeMap.put(certAttrName, userAttrType);
    }

    // Make sure that all the user attributes are configured with equality
    // indexes in all appropriate backends.
    Set<DN> cfgBaseDNs = configuration.getUserBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (DN baseDN : cfgBaseDNs)
    {
      for (AttributeType t : attributeMap.values())
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
    currentConfig
        .removeSubjectAttributeToUserAttributeChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    SubjectAttributeToUserAttributeCertificateMapperCfg config =
         currentConfig;
    LinkedHashMap<String,AttributeType> theAttributeMap = this.attributeMap;


    // Make sure that a peer certificate was provided.
    if ((certificateChain == null) || (certificateChain.length == 0))
    {
      Message message = ERR_SATUACM_NO_PEER_CERTIFICATE.get();
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

      Message message = ERR_SATUACM_PEER_CERT_NOT_X509.get(
          String.valueOf(certificateChain[0].getType()));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the subject from the peer certificate and use it to create a search
    // filter.
    DN peerDN;
    X500Principal peerPrincipal = peerCertificate.getSubjectX500Principal();
    String peerName = peerPrincipal.getName(X500Principal.RFC2253);
    try
    {
      peerDN = DN.decode(peerName);
    }
    catch (DirectoryException de)
    {
      Message message = ERR_SATUACM_CANNOT_DECODE_SUBJECT_AS_DN.get(
          peerName, de.getMessageObject());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   de);
    }

    LinkedList<SearchFilter> filterComps = new LinkedList<SearchFilter>();
    for (int i=0; i < peerDN.getNumComponents(); i++)
    {
      RDN rdn = peerDN.getRDN(i);
      for (int j=0; j < rdn.getNumValues(); j++)
      {
        String lowerName = toLowerCase(rdn.getAttributeName(j));

        // Try to normalize lowerName
        lowerName = normalizeAttributeName(lowerName);

        AttributeType attrType = theAttributeMap.get(lowerName);
        if (attrType != null)
        {
          filterComps.add(SearchFilter.createEqualityFilter(attrType,
                                            rdn.getAttributeValue(j)));
        }
      }
    }

    if (filterComps.isEmpty())
    {
      Message message = ERR_SATUACM_NO_MAPPABLE_ATTRIBUTES.get(
           String.valueOf(peerDN));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }

    SearchFilter filter = SearchFilter.createANDFilter(filterComps);


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
          Message message = ERR_SATUACM_MULTIPLE_SEARCH_MATCHING_ENTRIES.get(
                        String.valueOf(peerDN));
          throw new DirectoryException(
                  ResultCode.INVALID_CREDENTIALS, message);


        case TIME_LIMIT_EXCEEDED:
        case ADMIN_LIMIT_EXCEEDED:
          // The search criteria was too inefficient.
          message = ERR_SATUACM_INEFFICIENT_SEARCH.get(
                         String.valueOf(peerDN),
                         String.valueOf(searchOperation.getErrorMessage()));
          throw new DirectoryException(searchOperation.getResultCode(),
              message);

        default:
          // Just pass on the failure that was returned for this search.
          message = ERR_SATUACM_SEARCH_FAILED.get(
                         String.valueOf(peerDN),
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
          Message message = ERR_SATUACM_MULTIPLE_MATCHING_ENTRIES.
              get(String.valueOf(peerDN), String.valueOf(userEntry.getDN()),
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
    SubjectAttributeToUserAttributeCertificateMapperCfg config =
         (SubjectAttributeToUserAttributeCertificateMapperCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
              SubjectAttributeToUserAttributeCertificateMapperCfg
                   configuration,
              List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();

    // Get and validate the subject attribute to user attribute mappings.
    LinkedHashMap<String,AttributeType> newAttributeMap =
         new LinkedHashMap<String,AttributeType>();
mapLoop:
    for (String mapStr : configuration.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        unacceptableReasons.add(ERR_SATUACM_INVALID_MAP_FORMAT.get(
                String.valueOf(cfgEntryDN),
                mapStr));
        configAcceptable = false;
        break;
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if ((certAttrName.length() == 0) || (userAttrName.length() == 0))
      {
        unacceptableReasons.add(ERR_SATUACM_INVALID_MAP_FORMAT.get(
                String.valueOf(cfgEntryDN),
                mapStr));
        configAcceptable = false;
        break;
      }

      // Try to normalize the provided certAttrName
      certAttrName = normalizeAttributeName(certAttrName);

      if (newAttributeMap.containsKey(certAttrName))
      {
        unacceptableReasons.add(ERR_SATUACM_DUPLICATE_CERT_ATTR.get(
                String.valueOf(cfgEntryDN),
                certAttrName));
        configAcceptable = false;
        break;
      }

      AttributeType userAttrType =
           DirectoryServer.getAttributeType(userAttrName, false);
      if (userAttrType == null)
      {
        unacceptableReasons.add(ERR_SATUACM_NO_SUCH_ATTR.get(
                mapStr,
                String.valueOf(cfgEntryDN),
                userAttrName));
        configAcceptable = false;
        break;
      }

      for (AttributeType attrType : newAttributeMap.values())
      {
        if (attrType.equals(userAttrType))
        {
          unacceptableReasons.add(ERR_SATUACM_DUPLICATE_USER_ATTR.get(
                  String.valueOf(cfgEntryDN),
                  attrType.getNameOrOID()));
          configAcceptable = false;
          break mapLoop;
        }
      }

      newAttributeMap.put(certAttrName, userAttrType);
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
              SubjectAttributeToUserAttributeCertificateMapperCfg
                   configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get and validate the subject attribute to user attribute mappings.
    LinkedHashMap<String,AttributeType> newAttributeMap =
         new LinkedHashMap<String,AttributeType>();
mapLoop:
    for (String mapStr : configuration.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }


        messages.add(ERR_SATUACM_INVALID_MAP_FORMAT.get(
                String.valueOf(configEntryDN), mapStr));
        break;
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if ((certAttrName.length() == 0) || (userAttrName.length() == 0))
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }


        messages.add(ERR_SATUACM_INVALID_MAP_FORMAT.get(
                String.valueOf(configEntryDN), mapStr));
        break;
      }

      // Try to normalize the provided certAttrName
      certAttrName = normalizeAttributeName(certAttrName);

      if (newAttributeMap.containsKey(certAttrName))
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }


        messages.add(ERR_SATUACM_DUPLICATE_CERT_ATTR.get(
                String.valueOf(configEntryDN),
                certAttrName));
        break;
      }

      AttributeType userAttrType =
           DirectoryServer.getAttributeType(userAttrName, false);
      if (userAttrType == null)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }


        messages.add(ERR_SATUACM_NO_SUCH_ATTR.get(
                mapStr, String.valueOf(configEntryDN),
                userAttrName));
        break;
      }

      for (AttributeType attrType : newAttributeMap.values())
      {
        if (attrType.equals(userAttrType))
        {
          if (resultCode == ResultCode.SUCCESS)
          {
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
          }


          messages.add(ERR_SATUACM_DUPLICATE_USER_ATTR.get(
                  String.valueOf(configEntryDN),
                  attrType.getNameOrOID()));
          break mapLoop;
        }
      }

      newAttributeMap.put(certAttrName, userAttrType);
    }

    // Make sure that all the user attributes are configured with equality
    // indexes in all appropriate backends.
    Set<DN> cfgBaseDNs = configuration.getUserBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (DN baseDN : cfgBaseDNs)
    {
      for (AttributeType t : newAttributeMap.values())
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
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      attributeMap  = newAttributeMap;
      currentConfig = configuration;
    }


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Tries to normalize the given attribute name; if normalization is not
   * possible the original String value is returned.
   *
   * @param   attrName  The attribute name which should be normalized.
   *
   * @return  The normalized attribute name.
   */
  private static String normalizeAttributeName(String attrName)
  {
    AttributeType attrType =
         DirectoryServer.getAttributeType(attrName, false);
    if (attrType != null)
    {
      String attrNameNormalized = attrType.getNormalizedPrimaryName();
      if (attrNameNormalized != null)
      {
         attrName = attrNameNormalized;
      }
    }
    return attrName;
  }
}

