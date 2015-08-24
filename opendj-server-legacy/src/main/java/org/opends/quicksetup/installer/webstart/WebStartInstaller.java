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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.quicksetup.installer.webstart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.LicenseFile;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ZipExtractor;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.FileManager;
import org.opends.server.util.SetupUtils;

import static org.opends.messages.QuickSetupMessages.*;
import static com.forgerock.opendj.util.OperatingSystem.isWindows;
import static com.forgerock.opendj.cli.Utils.getThrowableMsg;

/**
 * This is an implementation of the Installer class that is used to install
 * the Directory Server using Web Start.
 *
 * It just takes a UserData object and based on that installs OpenDS.
 *
 *
 * This object has as parameter a WebStartDownloader object that is downloading
 * some jar files.  Until the WebStartDownloader has not finished downloading
 * the jar files will be on the ProgressStep.DOWNLOADING step because
 * we require all the jar files to be downloaded in order to install and
 * configure the Directory Server.
 *
 * Based on the Java properties set through the QuickSetup.jnlp file this
 * class will retrieve the zip file containing the install, unzip it and extract
 * it in the path specified by the user and that is contained in the
 * UserData object.
 *
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The notification
 * will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 */
public class WebStartInstaller extends Installer {
  private final Map<ProgressStep, Integer> hmRatio = new HashMap<>();
  private final Map<ProgressStep, LocalizableMessage> hmSummary = new HashMap<>();

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * WebStartInstaller constructor.
   */
  public WebStartInstaller()
  {
    initLoader();
    setCurrentProgressStep(InstallProgressStep.NOT_STARTED);
  }

  /** Actually performs the install in this thread.  The thread is blocked. */
  @Override
  public void run()
  {
    initMaps();
    PrintStream origErr = System.err;
    PrintStream origOut = System.out;
    boolean downloadedBits = false;
    try
    {
      System.setErr(getApplicationErrorStream());
      System.setOut(getApplicationOutputStream());

      setCurrentProgressStep(InstallProgressStep.DOWNLOADING);

      notifyListenersOfLog();
      notifyListeners(getLineBreak());

      checkAbort();

      InputStream in =
          getZipInputStream(getRatio(InstallProgressStep.EXTRACTING));

      setCurrentProgressStep(InstallProgressStep.EXTRACTING);
      if (isVerbose())
      {
        notifyListeners(getTaskSeparator());
      }

      checkAbort();

      createParentDirectoryIfRequired();
      extractZipFiles(in, getRatio(InstallProgressStep.EXTRACTING),
          getRatio(InstallProgressStep.CONFIGURING_SERVER));
      downloadedBits = true;

      try
      {
        in.close();
      }
      catch (Throwable t)
      {
        logger.info(LocalizableMessage.raw("Error closing zip input stream: "+t, t));
      }

      checkAbort();

      setCurrentProgressStep(InstallProgressStep.CONFIGURING_SERVER);
      if (isVerbose())
      {
        notifyListeners(getTaskSeparator());
      }
      configureServer();

      checkAbort();

      // create license accepted file
      LicenseFile.createFileLicenseApproved(getInstallationPath());

      createData();

      checkAbort();

      if (isWindows() && getUserData().getEnableWindowsService())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
        enableWindowsService();
        checkAbort();
      }

      if (mustStart())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.STARTING_SERVER);
        PointAdder pointAdder = new PointAdder();
        if (!isVerbose())
        {
          notifyListeners(getFormattedProgress(
              INFO_PROGRESS_STARTING_NON_VERBOSE.get()));
          pointAdder.start();
        }
        try
        {
          new ServerController(this).startServer(!isStartVerbose());
        }
        finally
        {
          if (!isVerbose())
          {
            pointAdder.stop();
          }
        }
        if (!isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
        else
        {
          notifyListeners(getLineBreak());
        }
        checkAbort();
      }

      if (mustCreateAds())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.CONFIGURING_ADS);
        updateADS();
        checkAbort();
      }

      if (mustConfigureReplication())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.CONFIGURING_REPLICATION);
        createReplicatedBackendsIfRequired();
        configureReplication();
        checkAbort();
      }

      if (mustInitializeSuffixes())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(
            InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
        initializeSuffixes();
        checkAbort();
      }

      if (mustStop())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.STOPPING_SERVER);
        if (!isVerbose())
        {
          notifyListeners(getFormattedWithPoints(
              INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
        }
        new ServerController(this).stopServer(!isVerbose());
        if (!isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
      }

      checkAbort();
      updateSummaryWithServerState(hmSummary, false);
      setCurrentProgressStep(InstallProgressStep.FINISHED_SUCCESSFULLY);
      notifyListeners(null);

    } catch (ApplicationException ex)
    {
      if (ReturnCode.CANCELED.equals(ex.getType())) {
        uninstall(downloadedBits);

        setCurrentProgressStep(InstallProgressStep.FINISHED_CANCELED);
        notifyListeners(null);
      } else {
        // Stop the server if necessary
        Installation installation = getInstallation();
        if (installation.getStatus().isServerRunning()) {
          try {
            if (!isVerbose())
            {
              notifyListeners(getFormattedWithPoints(
                  INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
            }
            new ServerController(installation).stopServer(!isVerbose());
            if (!isVerbose())
            {
              notifyListeners(getFormattedDoneWithLineBreak());
            }
          } catch (Throwable t) {
            logger.info(LocalizableMessage.raw("error stopping server", t));
          }
        }
        notifyListeners(getLineBreak());
        updateSummaryWithServerState(hmSummary, false);
        setCurrentProgressStep(InstallProgressStep.FINISHED_WITH_ERROR);
        LocalizableMessage html = getFormattedError(ex, true);
        notifyListeners(html);
        logger.error(LocalizableMessage.raw("Error installing.", ex));
        notifyListeners(getLineBreak());
        notifyListenersOfLogAfterError();
      }
    }
    catch (Throwable t)
    {
      // Stop the server if necessary
      Installation installation = getInstallation();
      if (installation.getStatus().isServerRunning()) {
        try {
          new ServerController(installation).stopServer(true);
        } catch (Throwable t2) {
          logger.info(LocalizableMessage.raw("error stopping server", t2));
        }
      }
      notifyListeners(getLineBreak());
      updateSummaryWithServerState(hmSummary, false);
      setCurrentProgressStep(InstallProgressStep.FINISHED_WITH_ERROR);
      ApplicationException ex = new ApplicationException(
          ReturnCode.BUG,
          getThrowableMsg(INFO_BUG_MSG.get(), t), t);
      LocalizableMessage msg = getFormattedError(ex, true);
      notifyListeners(msg);
      logger.error(LocalizableMessage.raw("Error installing.", t));
      notifyListeners(getLineBreak());
      notifyListenersOfLogAfterError();
    }
    System.setErr(origErr);
    System.setOut(origOut);
  }

  /** {@inheritDoc} */
  @Override
  public Integer getRatio(ProgressStep status)
  {
    return hmRatio.get(status);
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getSummary(ProgressStep status)
  {
    LocalizableMessage summary;
    if (InstallProgressStep.DOWNLOADING.equals(status)) {
      summary = loader.getSummary();
    } else {
      summary = hmSummary.get(status);
    }
    return summary;
  }

  /**
   * Initialize the different map used in this class.
   *
   */
  private void initMaps()
  {
    initSummaryMap(hmSummary, false);

    /*
     * hmTime contains the relative time that takes for each task to be
     * accomplished. For instance if downloading takes twice the time of
     * extracting, the value for downloading will be the double of the value for
     * extracting.
     */
    Map<ProgressStep, Integer> hmTime = new HashMap<>();
    hmTime.put(InstallProgressStep.DOWNLOADING, 30);
    hmTime.put(InstallProgressStep.EXTRACTING, 15);
    hmTime.put(InstallProgressStep.CONFIGURING_SERVER, 5);
    hmTime.put(InstallProgressStep.CREATING_BASE_ENTRY, 10);
    hmTime.put(InstallProgressStep.IMPORTING_LDIF, 20);
    hmTime.put(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED, 20);
    hmTime.put(InstallProgressStep.CONFIGURING_REPLICATION, 10);
    hmTime.put(InstallProgressStep.ENABLING_WINDOWS_SERVICE, 5);
    hmTime.put(InstallProgressStep.STARTING_SERVER, 10);
    hmTime.put(InstallProgressStep.STOPPING_SERVER, 5);
    hmTime.put(InstallProgressStep.CONFIGURING_ADS, 5);
    hmTime.put(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES, 25);

    int totalTime = 0;
    List<InstallProgressStep> steps = new ArrayList<>();
    totalTime += hmTime.get(InstallProgressStep.DOWNLOADING);
    steps.add(InstallProgressStep.DOWNLOADING);
    totalTime += hmTime.get(InstallProgressStep.EXTRACTING);
    steps.add(InstallProgressStep.EXTRACTING);
    totalTime += hmTime.get(InstallProgressStep.CONFIGURING_SERVER);
    steps.add(InstallProgressStep.CONFIGURING_SERVER);

    if (createNotReplicatedSuffix())
    {
      switch (getUserData().getNewSuffixOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        steps.add(InstallProgressStep.CREATING_BASE_ENTRY);
        totalTime += hmTime.get(InstallProgressStep.CREATING_BASE_ENTRY);
        break;
      case IMPORT_FROM_LDIF_FILE:
        steps.add(InstallProgressStep.IMPORTING_LDIF);
        totalTime += hmTime.get(InstallProgressStep.IMPORTING_LDIF);
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        steps.add(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED);
        totalTime +=hmTime.get(
            InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED);
        break;
      }
    }

    if (isWindows() && getUserData().getEnableWindowsService())
    {
        totalTime += hmTime.get(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
        steps.add(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
    }
    if (mustStart())
    {
      totalTime += hmTime.get(InstallProgressStep.STARTING_SERVER);
      steps.add(InstallProgressStep.STARTING_SERVER);
    }

    if (mustCreateAds())
    {
      totalTime += hmTime.get(InstallProgressStep.CONFIGURING_ADS);
      steps.add(InstallProgressStep.CONFIGURING_ADS);
    }

    if (mustConfigureReplication())
    {
      steps.add(InstallProgressStep.CONFIGURING_REPLICATION);
      totalTime += hmTime.get(InstallProgressStep.CONFIGURING_REPLICATION);
    }

    if (mustInitializeSuffixes())
    {
      totalTime += hmTime.get(
          InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
      steps.add(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
    }

    if (mustStop())
    {
      totalTime += hmTime.get(InstallProgressStep.STOPPING_SERVER);
      steps.add(InstallProgressStep.STOPPING_SERVER);
    }

    int cumulatedTime = 0;
    for (InstallProgressStep s : steps)
    {
      Integer statusTime = hmTime.get(s);
      hmRatio.put(s, (100 * cumulatedTime) / totalTime);
      if (statusTime != null)
      {
        cumulatedTime += statusTime;
      }
    }

    hmRatio.put(InstallProgressStep.FINISHED_SUCCESSFULLY, 100);
    hmRatio.put(InstallProgressStep.FINISHED_CANCELED, 100);
    hmRatio.put(InstallProgressStep.FINISHED_WITH_ERROR, 100);
  }

  private InputStream getZipInputStream(Integer maxRatio)
      throws ApplicationException {
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_DOWNLOADING.get()));

    waitForLoader(maxRatio);

    String zipName = getZipFileName();
    InputStream in =
      Installer.class.getClassLoader().getResourceAsStream(zipName);

    if (in == null)
    {
      throw new ApplicationException(
          ReturnCode.DOWNLOAD_ERROR,
              INFO_ERROR_ZIPINPUTSTREAMNULL.get(zipName), null);
    }

    notifyListeners(getFormattedDoneWithLineBreak());
    return in;
  }

  /**
   * Creates the parent Directory for the server location if it does not exist.
   * @throws ApplicationException if something goes wrong.
   */
  private void createParentDirectoryIfRequired() throws ApplicationException
  {
    String serverLocation = getUserData().getServerLocation();
    if (!Utils.parentDirectoryExists(serverLocation))
    {
      File f = new File(serverLocation);
      String parent = f.getParent();
      try
      {
        if (!Utils.createDirectory(parent))
        {
          throw new ApplicationException(
              ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_COULD_NOT_CREATE_PARENT_DIR.get(parent), null);
        }
      }
      catch (IOException ioe)
      {
        throw new ApplicationException(
            ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
            INFO_ERROR_COULD_NOT_CREATE_PARENT_DIR.get(parent),
            ioe);
      }
    }
  }

  /**
   * This method extracts the zip file.
   * @param is the input stream with the contents of the zip file.
   * @param minRatio the value of the ratio in the install that corresponds to
   * the moment where we start extracting the zip files.  Used to update
   * properly the install progress ratio.
   * @param maxRatio the value of the ratio in the installation that corresponds
   * to the moment where we finished extracting the last zip file.  Used to
   * update properly the install progress ratio.
   * @throws ApplicationException if an error occurs.
   */
  private void extractZipFiles(InputStream is, int minRatio, int maxRatio)
      throws ApplicationException {
    ZipExtractor extractor =
            new ZipExtractor(is, minRatio, maxRatio,
            Utils.getNumberZipEntries(),
            getZipFileName(),
            this);
    extractor.extract(getUserData().getServerLocation());
  }

  /**
   * Returns the name of the zip file name that contains all the installation.
   * @return the name of the zip file name that contains all the installation.
   */
  private String getZipFileName()
  {
    // Passed as a java option in the JNLP file
    return System.getProperty(SetupUtils.ZIP_FILE_NAME);
  }

  /**
   * Uninstall what has already been installed.
   * @param downloadedBits whether the bits were downloaded or not.
   */
  private void uninstall(boolean downloadedBits) {
    if (downloadedBits)
    {
      notifyListeners(getTaskSeparator());
      if (!isVerbose())
      {
        notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CANCELING.get()));
      }
      else
      {
        notifyListeners(
            getFormattedProgressWithLineBreak(INFO_SUMMARY_CANCELING.get()));
      }
      Installation installation = getInstallation();
      FileManager fm = new FileManager(this);

      // Stop the server if necessary
      if (installation.getStatus().isServerRunning()) {
        try {
          new ServerController(installation).stopServer(true);
        } catch (ApplicationException e) {
          logger.info(LocalizableMessage.raw("error stopping server", e));
        }
      }

      uninstallServices();

      try {
        fm.deleteRecursively(installation.getRootDirectory(), null,
            FileManager.DeletionPolicy.DELETE_ON_EXIT_IF_UNSUCCESSFUL);
      } catch (ApplicationException e) {
        logger.info(LocalizableMessage.raw("error deleting files", e));
      }
    }
    else
    {
      if (!isVerbose())
      {
        notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CANCELING.get()));
      }
      else
      {
        notifyListeners(
            getFormattedProgressWithLineBreak(INFO_SUMMARY_CANCELING.get()));
      }
      File serverRoot = new File(getUserData().getServerLocation());
      if (serverRoot.exists())
      {
        FileManager fm = new FileManager(this);
        try {
          fm.deleteRecursively(serverRoot, null,
              FileManager.DeletionPolicy.DELETE_ON_EXIT_IF_UNSUCCESSFUL);
        } catch (ApplicationException e) {
          logger.info(LocalizableMessage.raw("error deleting files", e));
        }
      }
    }
    if (!isVerbose())
    {
      notifyListeners(getFormattedDoneWithLineBreak());
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getInstallationPath()
  {
    return getUserData().getServerLocation();
  }

  /** {@inheritDoc} */
  @Override
  public String getInstancePath()
  {
    return getUserData().getServerLocation();
  }
}
