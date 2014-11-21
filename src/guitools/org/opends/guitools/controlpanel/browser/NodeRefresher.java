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
 *      Portions Copyright 2012-2013 ForgeRock AS
 */

package org.opends.guitools.controlpanel.browser;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Set;

import javax.naming.InterruptedNamingException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeNode;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.messages.AdminToolMessages;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchScope;

/**
 * The class that is in charge of doing the LDAP searches required to update a
 * node: search the local entry, detect if it has children, retrieve the
 * attributes required to render the node, etc.
 */
public class NodeRefresher extends AbstractNodeTask {

  /**
   * The enumeration containing all the states the refresher can have.
   *
   */
  public enum State
  {
    /**
     * The refresher is queued, but not started.
     */
    QUEUED,
    /**
     * The refresher is reading the local entry.
     */
    READING_LOCAL_ENTRY,
    /**
     * The refresher is solving a referral.
     */
    SOLVING_REFERRAL,
    /**
     * The refresher is detecting whether the entry has children or not.
     */
    DETECTING_CHILDREN,
    /**
     * The refresher is searching for the children of the entry.
     */
    SEARCHING_CHILDREN,
    /**
     * The refresher is finished.
     */
    FINISHED,
    /**
     * The refresher is cancelled.
     */
    CANCELLED,
    /**
     * The refresher has been interrupted.
     */
    INTERRUPTED,
    /**
     * The refresher has failed.
     */
    FAILED
  }

  BrowserController controller;
  State state;
  boolean recursive;

  SearchResult localEntry;
  SearchResult remoteEntry;
  LDAPURL   remoteUrl;
  boolean isLeafNode;
  ArrayList<SearchResult> childEntries = new ArrayList<SearchResult>();
  boolean differential;
  Exception exception;
  Object exceptionArg;


  /**
   * The constructor of the refresher object.
   * @param node the node on the tree to be updated.
   * @param ctlr the BrowserController.
   * @param localEntry the local entry corresponding to the node.
   * @param recursive whether this task is recursive or not (children must be
   * searched).
   */
  public NodeRefresher(BasicNode node, BrowserController ctlr,
      SearchResult localEntry, boolean recursive) {
    super(node);
    controller = ctlr;
    state = State.QUEUED;
    this.recursive = recursive;

    this.localEntry = localEntry;
  }


  /**
   * Returns the local entry the refresher is handling.
   * @return the local entry the refresher is handling.
   */
  public SearchResult getLocalEntry() {
    return localEntry;
  }


  /**
   * Returns the remote entry for the node.  It will be <CODE>null</CODE> if
   * the entry is not a referral.
   * @return the remote entry for the node.
   */
  public SearchResult getRemoteEntry() {
    return remoteEntry;
  }

  /**
   * Returns the URL of the remote entry.  It will be <CODE>null</CODE> if
   * the entry is not a referral.
   * @return the URL of the remote entry.
   */
  public LDAPURL getRemoteUrl() {
    return remoteUrl;
  }


  /**
   * Tells whether the node is a leaf or not.
   * @return <CODE>true</CODE> if the node is a leaf and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isLeafNode() {
    return isLeafNode;
  }


  /**
   * Returns the child entries of the node.
   * @return the child entries of the node.
   */
  public ArrayList<SearchResult> getChildEntries() {
    return childEntries;
  }

  /**
   * Returns whether this refresher object is working on differential mode or
   * not.
   * @return <CODE>true</CODE> if the refresher is working on differential
   * mode and <CODE>false</CODE> otherwise.
   */
  public boolean isDifferential() {
    return differential;
  }

  /**
   * Returns the exception that occurred during the processing.  It returns
   * <CODE>null</CODE> if no exception occurred.
   * @return the exception that occurred during the processing.
   */
  public Exception getException() {
    return exception;
  }


  /**
   * Returns the argument of the exception that occurred during the processing.
   * It returns <CODE>null</CODE> if no exception occurred or if the exception
   * has no arguments.
   * @return the argument exception that occurred during the processing.
   */
  public Object getExceptionArg() {
    return exceptionArg;
  }


  /**
   * Returns the displayed entry in the browser.  This depends on the
   * visualization options in the BrowserController.
   * @return the remote entry if the entry is a referral and the
   * BrowserController is following referrals and the local entry otherwise.
   */
  public SearchResult getDisplayedEntry() {
    SearchResult result;
    if (controller.getFollowReferrals() && (remoteEntry != null)) {
      result = remoteEntry;
    }
    else {
      result = localEntry;
    }
    return result;
  }

  /**
   * Returns the LDAP URL of the displayed entry in the browser.  This depends
   * on the visualization options in the BrowserController.
   * @return the remote entry LDAP URL if the entry is a referral and the
   * BrowserController is following referrals and the local entry LDAP URL
   * otherwise.
   */
  public LDAPURL getDisplayedUrl() {
    LDAPURL result;
    if (controller.getFollowReferrals() && (remoteUrl != null)) {
      result = remoteUrl;
    }
    else {
      result = controller.findUrlForLocalEntry(getNode());
    }
    return result;
  }

  /**
   * Returns whether the refresh is over or not.
   * @return <CODE>true</CODE> if the refresh is over and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isInFinalState() {
    return (
      (state == State.FINISHED) ||
        (state == State.CANCELLED) ||
        (state == State.FAILED) ||
        (state == State.INTERRUPTED)
    );
  }

  /**
   * The method that actually does the refresh.
   */
  @Override
  public void run() {
    final BasicNode node = getNode();

    try {
      boolean checkExpand = false;
      if (localEntry == null) {
        changeStateTo(State.READING_LOCAL_ENTRY);
        runReadLocalEntry();
      }
      if (!isInFinalState()) {
        if (controller.getFollowReferrals() && isReferralEntry(localEntry)) {
          changeStateTo(State.SOLVING_REFERRAL);
          runSolveReferral();
        }
        if (node.isLeaf()) {
          changeStateTo(State.DETECTING_CHILDREN);
          runDetectChildren();
        }
        if (controller.nodeIsExpanded(node) && recursive) {
          changeStateTo(State.SEARCHING_CHILDREN);
          runSearchChildren();
          /* If the node is not expanded, we have to refresh its children
            when we expand it */
        } else if (recursive  && (!node.isLeaf() || !isLeafNode)) {
          node.setRefreshNeededOnExpansion(true);
          checkExpand = true;
        }
        changeStateTo(State.FINISHED);
        if (checkExpand && mustAutomaticallyExpand(node))
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              controller.expandNode(node);
            }
          });
        }
      }
    }
    catch (NamingException ne)
    {
      exception = ne;
      exceptionArg = null;
    }
    catch(SearchAbandonException x) {
      exception = x.getException();
      exceptionArg = x.getArg();
      try {
        changeStateTo(x.getState());
      }
      catch(SearchAbandonException xx) {
        // We've done all what we can...
      }
    }
  }

  /**
   * Tells whether a custom filter is being used (specified by the user in the
   * browser dialog) or not.
   * @return <CODE>true</CODE> if a custom filter is being used and
   * <CODE>false</CODE> otherwise.
   */
  private boolean useCustomFilter()
  {
    boolean result=false;
    if (controller.getFilter()!=null)
      result =
        !controller.getFilter().equals(BrowserController.ALL_OBJECTS_FILTER);
    return result;
  }

  /**
   * Performs the search in the case the user specified a custom filter.
   * @param node the parent node we perform the search from.
   * @param ctx the connection to be used.
   * @throws NamingException if a problem occurred.
   */
  private void searchForCustomFilter(BasicNode node, InitialLdapContext ctx)
  throws NamingException
  {
    SearchControls ctls = controller.getBasicSearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(new String[] { SchemaConstants.NO_ATTRIBUTES });
    ctls.setCountLimit(1);
    NamingEnumeration<SearchResult> s = ctx.search(new LdapName(node.getDN()),
              controller.getFilter(),
              ctls);
    try
    {
      if (!s.hasMore())
      {
        throw new NameNotFoundException("Entry "+node.getDN()+
            " does not verify filter "+controller.getFilter());
      }
      while (s.hasMore())
      {
        s.next();
      }
    }
    catch (SizeLimitExceededException slme)
    {
      // We are just searching for an entry, but if there is more than one
      // this exception will be thrown.  We call sr.hasMore after the
      // first entry has been retrieved to avoid sending a systematic
      // abandon when closing the s NamingEnumeration.
      // See CR 6976906.
    }
    finally
    {
      s.close();
    }
  }

  /**
   * Performs the search in the case the user specified a custom filter.
   * @param dn the parent DN we perform the search from.
   * @param ctx the connection to be used.
   * @throws NamingException if a problem occurred.
   */
  private void searchForCustomFilter(String dn, InitialLdapContext ctx)
  throws NamingException
  {
    SearchControls ctls = controller.getBasicSearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(new String[]{});
    ctls.setCountLimit(1);
    NamingEnumeration<SearchResult> s = ctx.search(new LdapName(dn),
              controller.getFilter(),
              ctls);
    try
    {
      if (!s.hasMore())
      {
        throw new NameNotFoundException("Entry "+dn+
            " does not verify filter "+controller.getFilter());
      }
      while (s.hasMore())
      {
        s.next();
      }
    }
    catch (SizeLimitExceededException slme)
    {
      // We are just searching for an entry, but if there is more than one
      // this exception will be thrown.  We call sr.hasMore after the
      // first entry has been retrieved to avoid sending a systematic
      // abandon when closing the s NamingEnumeration.
      // See CR 6976906.
    }
    finally
    {
      s.close();
    }
  }

  /**
   * Read the local entry associated to the current node.
   */
  private void runReadLocalEntry() throws SearchAbandonException {
    BasicNode node = getNode();
    InitialLdapContext ctx = null;
    try {
      ctx = controller.findConnectionForLocalEntry(node);

      if (ctx != null) {
        if (useCustomFilter())
        {
          // Check that the entry verifies the filter
          searchForCustomFilter(node, ctx);
        }

        SearchControls ctls = controller.getBasicSearchControls();
        ctls.setReturningAttributes(controller.getAttrsForRedSearch());
        ctls.setSearchScope(SearchControls.OBJECT_SCOPE);

        NamingEnumeration<SearchResult> s =
                ctx.search(new LdapName(node.getDN()),
                controller.getObjectSearchFilter(),
                ctls);
        try
        {
          while (s.hasMore())
          {
            localEntry = s.next();
            localEntry.setName(node.getDN());

          }
        }
        finally
        {
          s.close();
        }
        if (localEntry == null) {
          /* Not enough rights to read the entry or the entry simply does not
           exist */
          throw new NameNotFoundException("Can't find entry: "+node.getDN());
        }
        throwAbandonIfNeeded(null);
      } else {
          changeStateTo(State.FINISHED);
      }
    }
    catch(NamingException x) {
        throwAbandonIfNeeded(x);
    }
    finally {
      if (ctx != null) {
        controller.releaseLDAPConnection(ctx);
      }
    }
  }

  /**
   * Solve the referral associated to the current node.
   * This routine assumes that node.getReferral() is non null
   * and that BrowserController.getFollowReferrals() == true.
   * It also protect the browser against looping referrals by
   * limiting the number of hops.
   * @throws SearchAbandonException if the hop count limit for referrals has
   * been exceeded.
   * @throws NamingException if an error occurred searching the entry.
   */
  private void runSolveReferral()
  throws SearchAbandonException, NamingException {
    int hopCount = 0;
    String[] referral = getNode().getReferral();
    while ((referral != null) && (hopCount < 10)) {
      readRemoteEntry(referral);
      referral = BrowserController.getReferral(remoteEntry);
      hopCount++;
    }
    if (referral != null) { // -> hopCount has reached the max
      throwAbandonIfNeeded(new ReferralLimitExceededException(
          AdminToolMessages.ERR_REFERRAL_LIMIT_EXCEEDED.get(hopCount)));
    }
  }

  /**
   * Searches for the remote entry.
   * @param referral the referral list to be used to search the remote entry.
   * @throws SearchAbandonException if an error occurs.
   */
  private void readRemoteEntry(String[] referral)
  throws SearchAbandonException {
    LDAPConnectionPool connectionPool = controller.getConnectionPool();
    LDAPURL url = null;
    SearchResult entry = null;
    String remoteDn = null;
    Exception lastException = null;
    Object lastExceptionArg = null;

    int i = 0;
    while ((i < referral.length) && (entry == null)) {
      InitialLdapContext ctx = null;
      try {
        url = LDAPURL.decode(referral[i], false);
        if (url.getHost() == null)
        {
          // Use the local server connection.
          ctx = controller.getUserDataConnection();
          url.setHost(ConnectionUtils.getHostName(ctx));
          url.setPort(ConnectionUtils.getPort(ctx));
          url.setScheme(ConnectionUtils.isSSL(ctx)?"ldaps":"ldap");
        }
        ctx = connectionPool.getConnection(url);
        remoteDn = url.getRawBaseDN();
        if ((remoteDn == null) ||
          remoteDn.equals("")) {
          /* The referral has not a target DN specified: we
             have to use the DN of the entry that contains the
             referral... */
          if (remoteEntry != null) {
            remoteDn = remoteEntry.getName();
          } else {
            remoteDn = localEntry.getName();
          }
          /* We have to recreate the url including the target DN
             we are using */
          url = new LDAPURL(url.getScheme(), url.getHost(), url.getPort(),
              remoteDn, url.getAttributes(), url.getScope(), url.getRawFilter(),
                 url.getExtensions());
        }
        if (useCustomFilter() && url.getScope() == SearchScope.BASE_OBJECT)
        {
          // Check that the entry verifies the filter
          searchForCustomFilter(remoteDn, ctx);
        }

        int scope = getJNDIScope(url);
        String filter = getJNDIFilter(url);

        SearchControls ctls = controller.getBasicSearchControls();
        ctls.setReturningAttributes(controller.getAttrsForBlackSearch());
        ctls.setSearchScope(scope);
        ctls.setCountLimit(1);
        NamingEnumeration<SearchResult> sr = ctx.search(remoteDn,
            filter,
            ctls);
        try
        {
          boolean found = false;
          while (sr.hasMore())
          {
            entry = sr.next();
            String name;
            if (entry.getName().length() == 0)
            {
              name = remoteDn;
            }
            else
            {
              name = unquoteRelativeName(entry.getName())+","+remoteDn;
            }
            entry.setName(name);
            found = true;
          }
          if (!found)
          {
            throw new NameNotFoundException();
          }
        }
        catch (SizeLimitExceededException sle)
        {
          // We are just searching for an entry, but if there is more than one
          // this exception will be thrown.  We call sr.hasMore after the
          // first entry has been retrieved to avoid sending a systematic
          // abandon when closing the sr NamingEnumeration.
          // See CR 6976906.
        }
        finally
        {
          sr.close();
        }
        throwAbandonIfNeeded(null);
      }
      catch (InterruptedNamingException x) {
        throwAbandonIfNeeded(x);
      }
      catch (NamingException x) {
        lastException = x;
        lastExceptionArg = referral[i];
      }
      catch (DirectoryException de) {
        lastException = de;
        lastExceptionArg = referral[i];
      }
      finally {
        if (ctx != null) {
          connectionPool.releaseConnection(ctx);
        }
      }
      i = i + 1;
    }
    if (entry == null) {
      throw new SearchAbandonException(
          State.FAILED, lastException, lastExceptionArg);
    }
    else
    {
      if (url.getScope() != SearchScope.BASE_OBJECT)
      {
        // The URL is to be transformed: the code assumes that the URL points
        // to the remote entry.
        url = new LDAPURL(url.getScheme(), url.getHost(),
            url.getPort(), entry.getName(), url.getAttributes(),
            SearchScope.BASE_OBJECT, null, url.getExtensions());
      }
      checkLoopInReferral(url, referral[i-1]);
      remoteUrl = url;
      remoteEntry = entry;
    }
  }

  /**
   * Tells whether the provided node must be automatically expanded or not.
   * This is used when the user provides a custom filter, in this case we
   * expand automatically the tree.
   * @param node the node to analyze.
   * @return <CODE>true</CODE> if the node must be expanded and
   * <CODE>false</CODE> otherwise.
   */
  private boolean mustAutomaticallyExpand(BasicNode node)
  {
    boolean mustAutomaticallyExpand = false;
    if (controller.isAutomaticExpand())
    {
      // Limit the number of expansion levels to 3
      int nLevels = 0;
      TreeNode parent = node;
      while (parent != null)
      {
        nLevels ++;
        parent = parent.getParent();
      }
      mustAutomaticallyExpand = nLevels <= 4;
    }
    return mustAutomaticallyExpand;
  }

  /**
   * Detects whether the entries has children or not.
   * @throws SearchAbandonException if the search was abandoned.
   * @throws NamingException if an error during the search occurred.
   */
  private void runDetectChildren()
  throws SearchAbandonException, NamingException {
    if (controller.isShowContainerOnly() || !isNumSubOrdinatesUsable()) {
      runDetectChildrenManually();
    }
    else {
      SearchResult entry = getDisplayedEntry();
      isLeafNode = !BrowserController.getHasSubOrdinates(entry);
    }
  }


  /**
   * Detects whether the entry has children by performing a search using the
   * entry as base DN.
   * @throws SearchAbandonException if there is an error.
   */
  private void runDetectChildrenManually() throws SearchAbandonException {
    BasicNode parentNode = getNode();
    InitialLdapContext ctx = null;
    NamingEnumeration<SearchResult> searchResults = null;

    try {
      // We set the search constraints so that only one entry is returned.
      // It's enough to know if the entry has children or not.
      SearchControls ctls = controller.getBasicSearchControls();
      ctls.setCountLimit(1);
      ctls.setReturningAttributes(
          new String[] { SchemaConstants.NO_ATTRIBUTES });
      if (useCustomFilter())
      {
        ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      }
      else
      {
        ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
      }
      // Send an LDAP search
      ctx = controller.findConnectionForDisplayedEntry(parentNode);
      searchResults = ctx.search(
          new LdapName(controller.findBaseDNForChildEntries(parentNode)),
          controller.getChildSearchFilter(),
          ctls);

      throwAbandonIfNeeded(null);
      isLeafNode = true;
      // Check if parentNode has children
      while (searchResults.hasMoreElements()) {
        isLeafNode = false;
      }
    }
    catch (SizeLimitExceededException e)
    {
      // We are just searching for an entry, but if there is more than one
      // this exception will be thrown.  We call sr.hasMore after the
      // first entry has been retrieved to avoid sending a systematic
      // abandon when closing the searchResults NamingEnumeration.
      // See CR 6976906.
    }
    catch (NamingException x) {
      throwAbandonIfNeeded(x);
    }
    finally {
      if (ctx != null) {
        controller.releaseLDAPConnection(ctx);
      }
      if (searchResults != null)
      {
        try
        {
          searchResults.close();
        }
        catch (NamingException x)
        {
          throwAbandonIfNeeded(x);
        }
      }
    }
  }


  // NUMSUBORDINATE HACK
  // numsubordinates is not usable if the displayed entry
  // is listed in in the hacker.
  // Note: *usable* means *usable for detecting children presence*.
  private boolean isNumSubOrdinatesUsable() throws NamingException {
    boolean result;
    SearchResult entry = getDisplayedEntry();
    boolean hasSubOrdinates = BrowserController.getHasSubOrdinates(entry);
    if (!hasSubOrdinates) { // We must check
      LDAPURL url = getDisplayedUrl();
      if (controller.getNumSubordinateHacker().contains(url)) {
        // The numSubOrdinate we have is unreliable.
        result = false;
//        System.out.println("numSubOrdinates of " + url +
//                           " is not reliable");
      }
      else {
        result = true;
      }
    }
    else { // Other values are usable
      result = true;
    }
    return result;
  }



  /**
   * Searchs for the children.
   * @throws SearchAbandonException if an error occurs.
   */
  private void runSearchChildren() throws SearchAbandonException {
    InitialLdapContext ctx = null;
    BasicNode parentNode = getNode();
    parentNode.setSizeLimitReached(false);

    try {
      // Send an LDAP search
      SearchControls ctls = controller.getBasicSearchControls();
      if (useCustomFilter())
      {
        ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      }
      else
      {
        ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      }
      ctls.setReturningAttributes(controller.getAttrsForRedSearch());
      ctx = controller.findConnectionForDisplayedEntry(parentNode);
      String parentDn = controller.findBaseDNForChildEntries(parentNode);
      int parentComponents;
      try
      {
        DN dn = DN.decode(parentDn);
        parentComponents = dn.getNumComponents();
      }
      catch (Throwable t)
      {
        throw new RuntimeException("Error decoding dn: "+parentDn+" . "+t,
            t);
      }
      NamingEnumeration<SearchResult> entries = ctx.search(
            new LdapName(parentDn),
                controller.getChildSearchFilter(),
                ctls);

      try
      {
        while (entries.hasMore())
        {
          SearchResult r = entries.next();
          String name;
          if (r.getName().length() == 0)
          {
            continue;
          }
          else
          {
            name = unquoteRelativeName(r.getName())+","+parentDn;
          }
          boolean add = false;
          if (useCustomFilter())
          {
            // Check that is an immediate child: use a faster method by just
            // comparing the number of components.
            DN dn = null;
            try
            {
              dn = DN.decode(name);
              add = dn.getNumComponents() == parentComponents + 1;
            }
            catch (Throwable t)
            {
              throw new RuntimeException("Error decoding dns: "+t, t);
            }

            if (!add)
            {
              // Is not a direct child.  Check if the parent has been added,
              // if it is the case, do not add the parent.  If is not the case,
              // search for the parent and add it.
              RDN[] rdns = new RDN[parentComponents + 1];
              int diff = dn.getNumComponents() - rdns.length;
              for (int i=0; i < rdns.length; i++)
              {
                rdns[i] = dn.getRDN(i + diff);
              }
              final DN parentToAddDN = new DN(rdns);
              boolean mustAddParent = true;
              for (SearchResult addedEntry : childEntries)
              {
                try
                {
                  DN addedDN = DN.decode(addedEntry.getName());
                  if (addedDN.equals(parentToAddDN))
                  {
                    mustAddParent = false;
                    break;
                  }
                }
                catch (Throwable t)
                {
                  throw new RuntimeException("Error decoding dn: "+
                      addedEntry.getName()+" . "+t, t);
                }
              }
              if (mustAddParent)
              {
                final boolean resultValue[] = {true};
                // Check the children added to the tree
                try
                {
                  SwingUtilities.invokeAndWait(new Runnable()
                  {
                    public void run()
                    {
                      for (int i=0; i<getNode().getChildCount(); i++)
                      {
                        BasicNode node = (BasicNode)getNode().getChildAt(i);
                        try
                        {
                          DN dn = DN.decode(node.getDN());
                          if (dn.equals(parentToAddDN))
                          {
                            resultValue[0] = false;
                            break;
                          }
                        }
                        catch (Throwable t)
                        {
                          throw new RuntimeException("Error decoding dn: "+
                              node.getDN()+" . "+t, t);
                        }
                      }
                    }
                  });
                }
                catch (Throwable t)
                {
                  // Ignore
                }
                mustAddParent = resultValue[0];
              }
              if (mustAddParent)
              {
                SearchResult parentResult = searchManuallyEntry(ctx,
                    parentToAddDN.toString());
                childEntries.add(parentResult);
              }
            }
          }
          else
          {
            add = true;
          }
          if (add)
          {
            r.setName(name);
            childEntries.add(r);
            // Time to time we update the display
            if (childEntries.size() >= 20) {
              changeStateTo(State.SEARCHING_CHILDREN);
              childEntries.clear();
            }
          }
          throwAbandonIfNeeded(null);
        }
      }
      finally
      {
        entries.close();
      }
    }
    catch (SizeLimitExceededException slee)
    {
      parentNode.setSizeLimitReached(true);
    }
    catch (NamingException x) {
      throwAbandonIfNeeded(x);
    }
    finally {
      if (ctx != null)
      {
        controller.releaseLDAPConnection(ctx);
      }
    }
  }

  /**
   * Returns the entry for the given dn.
   * The code assumes that the request controls are set in the connection.
   * @param ctx the connection to be used.
   * @param dn the DN of the entry to be searched.
   * @throws NamingException if an error occurs.
   */
  private SearchResult searchManuallyEntry(InitialLdapContext ctx, String dn)
  throws NamingException
  {
    SearchResult sr = null;
//  Send an LDAP search
    SearchControls ctls = controller.getBasicSearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(controller.getAttrsForRedSearch());
    NamingEnumeration<SearchResult> entries = ctx.search(
          new LdapName(dn),
              controller.getObjectSearchFilter(),
              ctls);

    try
    {
      while (entries.hasMore())
      {
        sr = entries.next();
        sr.setName(dn);
      }
    }
    finally
    {
      entries.close();
    }
    return sr;
  }


  /**
   * Utilities
   */


  /**
   * Change the state of the task and inform the BrowserController.
   * @param newState the new state for the refresher.
   */
  private void changeStateTo(State newState) throws SearchAbandonException {
    State oldState = state;
    state = newState;
    try {
      controller.invokeRefreshTaskDidProgress(this, oldState, newState);
    }
    catch(InterruptedException x) {
      throwAbandonIfNeeded(x);
    }
  }


  /**
   * Transform an exception into a TaskAbandonException.
   * If no exception is passed, the routine checks if the task has
   * been canceled and throws an TaskAbandonException accordingly.
   * @param x the exception.
   * @throws SearchAbandonException if the task/refresher must be abandoned.
   */
  private void throwAbandonIfNeeded(Exception x) throws SearchAbandonException {
    SearchAbandonException tax = null;
    if (x != null) {
      if ((x instanceof InterruptedException) ||
          (x instanceof InterruptedNamingException)) {
        tax = new SearchAbandonException(State.INTERRUPTED, x, null);
      }
      else {
        tax = new SearchAbandonException(State.FAILED, x, null);
      }
    }
    else if (isCanceled()) {
      tax = new SearchAbandonException(State.CANCELLED, null, null);
    }
    if (tax != null) {
      throw tax;
    }
  }

  /**
   * Removes the quotes surrounding the provided name.  JNDI can return relative
   * names with this format.
   * @param name the relative name to be treated.
   * @return an String representing the provided relative name without
   * surrounding quotes.
   */
  private String unquoteRelativeName(String name)
  {
    if ((name.length() > 0) && (name.charAt(0) == '"'))
    {
      if (name.charAt(name.length() - 1) == '"')
      {
        return name.substring(1, name.length() - 1);
      }
      else
      {
        return name.substring(1);
      }
    }
    else
    {
      return name;
    }
  }

  /**
   * DEBUG : Dump the state of the task.
   */
  void dump() {
    System.out.println("=============");
    System.out.println("         node: " + getNode().getDN());
    System.out.println("    recursive: " + recursive);
    System.out.println(" differential: " + differential);

    System.out.println("        state: " + state);
    System.out.println("   localEntry: " + localEntry);
    System.out.println("  remoteEntry: " + remoteEntry);
    System.out.println("    remoteUrl: " + remoteUrl);
    System.out.println("   isLeafNode: " + isLeafNode);
    System.out.println("    exception: " + exception);
    System.out.println(" exceptionArg: " + exceptionArg);
    System.out.println("=============");
  }


  /**
   * Checks that the entry's objectClass contains 'referral' and that the
   * attribute 'ref' is present.
   * @param entry the search result.
   * @return <CODE>true</CODE> if the entry's objectClass contains 'referral'
   * and the attribute 'ref' is present and <CODE>false</CODE> otherwise.
   * @throws NamingException if an error occurs.
   */
  static boolean isReferralEntry(SearchResult entry) throws NamingException {
    boolean result = false;
    Set<String> ocValues = ConnectionUtils.getValues(entry, "objectClass");
    if (ocValues != null) {
      for (String value : ocValues)
      {
        boolean isReferral = value.equalsIgnoreCase("referral");

        if (isReferral) {
          result = (ConnectionUtils.getFirstValue(entry, "ref") != null);
          break;
        }
      }
    }
    return result;
  }

  /**
   * Returns the scope to be used in a JNDI request based on the information
   * of an LDAP URL.
   * @param url the LDAP URL.
   * @return the scope to be used in a JNDI request.
   */
  private int getJNDIScope(LDAPURL url)
  {
    int scope;
    if (url.getScope() != null)
    {
      switch (url.getScope())
      {
      case BASE_OBJECT:
        scope = SearchControls.OBJECT_SCOPE;
        break;
      case WHOLE_SUBTREE:
        scope = SearchControls.SUBTREE_SCOPE;
        break;
      case SUBORDINATE_SUBTREE:
        scope = SearchControls.ONELEVEL_SCOPE;
        break;
      case SINGLE_LEVEL:
        scope = SearchControls.ONELEVEL_SCOPE;
        break;
      default:
        scope = SearchControls.OBJECT_SCOPE;
      }
    }
    else
    {
      scope = SearchControls.OBJECT_SCOPE;
    }
    return scope;
  }

  /**
   * Returns the filter to be used in a JNDI request based on the information
   * of an LDAP URL.
   * @param url the LDAP URL.
   * @return the filter.
   */
  private String getJNDIFilter(LDAPURL url)
  {
    String filter = url.getRawFilter();
    if (filter == null)
    {
      filter = controller.getObjectSearchFilter();
    }
    return filter;
  }

  /**
   * Check that there is no loop in terms of DIT (the check basically identifies
   * whether we are pointing to an entry above in the same server).
   * @param url the URL to the remote entry.  It is assumed that the base DN
   * of the URL points to the remote entry.
   * @param referral the referral used to retrieve the remote entry.
   * @throws SearchAbandonException if there is a loop issue (the remoteEntry
   * is actually an entry in the same server as the local entry but above in the
   * DIT).
   */
  private void checkLoopInReferral(LDAPURL url,
      String referral) throws SearchAbandonException
  {
    boolean checkSucceeded = true;
    try
    {
      DN dn1 = DN.decode(getNode().getDN());
      DN dn2 = url.getBaseDN();
      if (dn2.isAncestorOf(dn1))
      {
        String host = url.getHost();
        int port = url.getPort();
        String adminHost = ConnectionUtils.getHostName(
            controller.getConfigurationConnection());
        int adminPort =
          ConnectionUtils.getPort(controller.getConfigurationConnection());
        checkSucceeded = (port != adminPort) ||
        !adminHost.equalsIgnoreCase(host);

        if (checkSucceeded)
        {
          String hostUserData = ConnectionUtils.getHostName(
              controller.getUserDataConnection());
          int portUserData =
            ConnectionUtils.getPort(controller.getUserDataConnection());
          checkSucceeded = (port != portUserData) ||
          !hostUserData.equalsIgnoreCase(host);
        }
      }
    }
    catch (OpenDsException odse)
    {
      // Ignore
    }
    if (!checkSucceeded)
    {
      throw new SearchAbandonException(
          State.FAILED, new ReferralLimitExceededException(
              ERR_CTRL_PANEL_REFERRAL_LOOP.get(url.getRawBaseDN())), referral);
    }
  }
}
