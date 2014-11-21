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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.build.tools;



import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.SVNURL;



/**
 * This class provides an implementation of an Ant task that may be used to
 * determine the current Subversion revision number of the current working
 * copy.  The value of the revision number will be stored in an Ant property.
 */
public class GetSubversionUrlRepo
       extends Task
{
  // The name of the property in which the revision number should be set.
  private String propertyName = null;

  // The path to the root of the Subversion workspace for which to retrieve the
  // revision number.
  private String workspace = null;

  // The svn client manager. Required by svnkit 1.2.x
  private static SVNClientManager ourClientManager =
          SVNClientManager.newInstance();

  /**
   * Specifies the name of the Ant property into which the Subversion revision
   * number will be stored.
   *
   * @param  propertyName  The name of the Ant property into which the
   *                       Subversion revision number will be stored.
   */
  public void setProperty(String propertyName)
  {
    this.propertyName = propertyName;
  }



  /**
   * Specifies the path to the root of the Subversion workspace for which to
   * retrieve the revision number.
   *
   * @param  workspace  The path to the root of the Subversion workspace for
   *                    which to retrieve the revision number.
   */
  public void setWorkspace(String workspace)
  {
    this.workspace = workspace;
  }



  /**
   * Performs the appropriate processing needed for this task.  In this case,
   * it uses SVNKit to identify the current revision number for the local
   * workspace and store it in a specified property.
   */
  @Override()
  public void execute()
  {
    if ((propertyName == null) || (propertyName.length() == 0))
    {
      throw new BuildException("ERROR:  No property was specified for " +
                               "storing the revision number value.");
    }

    File workspacePath;
    if ((workspace == null) || (workspace.length() == 0))
    {
      workspacePath = getProject().getBaseDir();
    }
    else
    {
      workspacePath = new File(workspace);
    }


    try
    {
      SVNInfo svnInfo = ourClientManager.getWCClient().doInfo(workspacePath, SVNRevision.WORKING);
      SVNURL url_repo = svnInfo.getURL();
      
  
      if (url_repo == null)
      {
        System.err.println("WARNING:  Could not determine Subversion URL Repository " +
                           "for current workspace.");
        getProject().setNewProperty(propertyName, "-1");
      }
      else
      {
        getProject().setNewProperty(propertyName,
                                    String.valueOf(url_repo));

      }
  
    }
    catch (SVNException svnException)
    {
      System.err.println("WARNING:  Could not determine Subversion " +
                         "URL repository for current workspace:  " +
                         svnException);
      getProject().setNewProperty(propertyName, "-1");
    }
  }
}

