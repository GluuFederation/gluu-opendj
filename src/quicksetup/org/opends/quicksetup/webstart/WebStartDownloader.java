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
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.quicksetup.webstart;
import org.opends.messages.Message;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jnlp.DownloadService;
import javax.jnlp.DownloadServiceListener;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;


import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.SetupUtils;

import static org.opends.quicksetup.util.Utils.*;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is used to download the files that have been marked as lazy
 * in the QuickSetup.jnlp file.
 *
 * The global idea is to force the user to download just one jar file
 * (quicksetup.jar) to display the Web Start installer dialog.  Then QuickSetup
 * will call this class and it will download the jar files.  Until this class is
 * not finished the WebStartInstaller will be on the
 * ProgressStep.DOWNLOADING step.
 */
public class WebStartDownloader implements DownloadServiceListener {
  static private final Logger LOG =
    Logger.getLogger(WebStartDownloader.class.getName());

  private ApplicationException ex;

  private boolean isFinished;

  private int downloadPercentage = 0;

  private int currentPercMin = 0;

  private int currentPercMax = 0;

  private int currentValidatingPercent = 0;

  private int currentUpgradingPercent = 0;

  private Status status = Status.DOWNLOADING;

  private Message summary = null;

  /**
   * This enumeration contains the different Status on which
   * the dowloading process of the jars can be.
   *
   */
  public enum Status
    {
    /**
     * Downloading a jar file.
     */
    DOWNLOADING,
    /**
     * Validating a jar file.
     */
    VALIDATING,
    /**
     * Upgrading a jar file.
     */
    UPGRADING
    }

  /**
   * Creates a default instance.
   */
  public WebStartDownloader() {
    this.summary = INFO_DOWNLOADING.get();
  }

  /**
   * Starts the downloading of the jar files.  If forceDownload is set to
   * <CODE>true</CODE> the files will be re-downloaded even if they already
   * are on cache.
   *
   * This method does not block the thread that calls it.
   *
   * @param forceDownload used to ignore the case and force download.
   */
  public void start(final boolean forceDownload)
  {
    isFinished = false;
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          startDownload(forceDownload);
        } catch (ApplicationException ex)
        {
          WebStartDownloader.this.ex = ex;
        } catch (MalformedURLException mfe)
        {
          // This is a bug
          ex =
              new ApplicationException(ReturnCode.BUG,
                      getThrowableMsg(INFO_BUG_MSG.get(),mfe), mfe);
        } catch (IOException ioe)
        {
          StringBuilder buf = new StringBuilder();
          String[] jars = getJarUrls();
          for (int i = 0; i < jars.length; i++)
          {
            if (i != 0)
            {
              buf.append(",");
            }
            buf.append(jars[i]);
          }
          ex =
              new ApplicationException(
              ReturnCode.DOWNLOAD_ERROR,
              getThrowableMsg(
                      INFO_DOWNLOADING_ERROR.get(buf.toString()), ioe), ioe);
        } catch (Throwable t)
        {
          // This is a bug
          ex =
              new ApplicationException(ReturnCode.BUG,
                      getThrowableMsg(INFO_BUG_MSG.get(), t), t);
        }
      }
    });
    t.start();
  }

  /**
   * Gets a summary message of the downloader's current progress.
   * @return String for showing the user progress
   */
  public Message getSummary() {
    return this.summary;
  }

  /**
   * Sets a summary message of the downloader's current progress.
   * @param summary String for showing the user progress
   */
  public void setSummary(Message summary) {
    this.summary = summary;
  }

  /**
   * Returns <CODE>true</CODE> if the install is finished and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the install is finished and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isFinished()
  {
    return isFinished;
  }

  /**
   * Returns the Status of the current download process.
   * @return the current status of the download process.
   */
  public Status getStatus()
  {
    return status;
  }

  /**
   * Returns the current download percentage.
   * @return the current download percentage.
   */
  public int getDownloadPercentage()
  {
    return downloadPercentage;
  }

  /**
   * Returns the completed percentage for the file being currently validated.
   * @return the completed percentage for the file being currently validated.
   */
  public int getCurrentValidatingPercentage()
  {
    return currentValidatingPercent;
  }

  /**
   * Returns the completed percentage for the file being currently upgraded.
   * @return the completed percentage for the file being currently upgraded.
   */
  public int getCurrentUpgradingPercentage()
  {
    return currentUpgradingPercent;
  }

  /**
   * Starts synchronously the downloading on this thread.  The thread calling
   * this method will be blocked.  If forceDownload is set to
   * <CODE>true</CODE> the files will be re-downloaded even if they already
   * are on cache.
   * @param forceDownload used to ignore the case and force download.
   * @throws MalformedURLException if there is an error with the URLs that we
   * get from the property SetupUtils.LAZY_JAR_URLS
   * @throws IOException if a network problem occurs.
   * @throws ApplicationException if the download service is not available.
   */
  private void startDownload(boolean forceDownload)
      throws IOException, ApplicationException
  {
    DownloadService ds;
    try
    {
      ds =
          (DownloadService) ServiceManager.lookup(Utils.JNLP_SERVICE_NAME);
    } catch (UnavailableServiceException e)
    {
      LOG.log(Level.SEVERE, "Could not find service: "+
          Utils.JNLP_SERVICE_NAME, e);
      String setupFile;
      if (Utils.isWindows())
      {
        setupFile = Installation.WINDOWS_SETUP_FILE_NAME;
      }
      else
      {
        setupFile = Installation.UNIX_SETUP_FILE_NAME;
      }
      throw new ApplicationException(
        ReturnCode.DOWNLOAD_ERROR,
        getThrowableMsg(INFO_DOWNLOADING_ERROR_NO_SERVICE_FOUND.get(
            Utils.JNLP_SERVICE_NAME, setupFile),
            e), e);
    }

    String[] urls = getJarUrls();
    String[] versions = getJarVersions();

    /*
     * Calculate the percentages that correspond to each file.
     * TODO ideally this should be done dynamically, but as this is just
     * to provide progress, updating this information from time to time can
     * be enough and does not complexify the build process.
     */
    int[] percentageMax = new int[urls.length];
    int[] ratios = new int[urls.length];
    int totalRatios = 0;
    for (int i=0; i<percentageMax.length; i++)
    {
      int ratio;
      if (urls[i].endsWith("OpenDS.jar"))
      {
        ratio =  23;
      }
      else if (urls[i].endsWith("je.jar"))
      {
        ratio = 11;
      }
      else if (urls[i].endsWith("zipped.jar"))
      {
        ratio = 110;
      }
      else if (urls[i].endsWith("aspectjrt.jar"))
      {
        ratio = 10;
      }
      else
      {
        ratio = (100 / urls.length);
      }
      ratios[i] = ratio;
      totalRatios += ratio;
    }

    for (int i=0; i<percentageMax.length; i++)
    {
      int r = 0;
      for (int j=0; j<=i; j++)
      {
        r += ratios[j];
      }
      percentageMax[i] = (100 * r)/totalRatios;
    }


    for (int i = 0; i < urls.length && (getException() == null); i++)
    {
      if (i == 0)
      {
        currentPercMin = 0;
      }
      else {
        currentPercMin = percentageMax[i-1];
      }
      currentPercMax = percentageMax[i];

      // determine if a particular resource is cached
      String sUrl = urls[i];
      String version = versions[i];

      URL url = new URL(sUrl);
      boolean cached = ds.isResourceCached(url, version);

      if (cached && forceDownload)
      {
        try
        {
          ds.removeResource(url, version);
        } catch (IOException ioe)
        {
        }
        cached = false;
      }

      if (!cached)
      {
        // if not in the cache load the resource into the cache
        ds.loadResource(url, version, this);
      }
      downloadPercentage = currentPercMax;
    }
    isFinished = true;
  }

  /**
   * Returns the ApplicationException that has occurred during the download or
   * <CODE>null</CODE> if no exception occurred.
   * @return the ApplicationException that has occurred during the download or
   * <CODE>null</CODE> if no exception occurred.
   */
  public ApplicationException getException()
  {
    return ex;
  }

  /**
   * {@inheritDoc}
   */
  public void downloadFailed(URL url, String version)
  {
    ex =
        new ApplicationException(
        ReturnCode.DOWNLOAD_ERROR,
                INFO_DOWNLOADING_ERROR.get(url.toString()), null);
  }

  /**
   * {@inheritDoc}
   */
  public void progress(URL url, String version, long readSoFar, long total,
      int overallPercent)
  {
    if (overallPercent >= 0)
    {
      downloadPercentage = getPercentage(overallPercent);
    }
    status = Status.DOWNLOADING;
  }

  /**
   * {@inheritDoc}
   */
  public void upgradingArchive(URL url, String version, int patchPercent,
      int overallPercent)
  {
    currentUpgradingPercent = overallPercent;
    status = Status.UPGRADING;
  }

  /**
   * {@inheritDoc}
   */
  public void validating(URL url, String version, long entry, long total,
      int overallPercent)
  {
    if (total > 0)
    {
      currentValidatingPercent = (int)((100 * entry) / total);
    }
    else {
      currentValidatingPercent = 0;
    }

    status = Status.VALIDATING;
  }

  /**
   * Returns the jar files in a String[] from the System properties.
   * @return the jar files from the System properties.
   */
  private String[] getJarUrls()
  {
    String jars = System.getProperty(SetupUtils.LAZY_JAR_URLS);
    return jars.split(" ");
  }


  /**
   * Returns the downloaded percentage based on how much of the current jar file
   * has been downloaded.
   * @param currentJarRatio the download ratio of the jar file that is being
   * currently downloaded.
   * @return the downloaded percentage based on how much of the current jar file
   * has been downloaded.
   */
  private int getPercentage(int currentJarRatio)
  {
    return currentPercMin
            + (currentJarRatio * (currentPercMax - currentPercMin) / 100);
  }

  /**
   * Returns the java jar versions in a String[].  Currently just returns some
   * null strings.
   * @return the java jar versions in a String[].
   */
  private String[] getJarVersions()
  {
    return new String[getJarUrls().length];
  }

}
