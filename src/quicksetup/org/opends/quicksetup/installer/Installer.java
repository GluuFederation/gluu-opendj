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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.quicksetup.installer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.WindowEvent;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NamingSecurityException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.TopologyCacheFilter;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.JavaArguments;
import org.opends.quicksetup.LicenseFile;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.UserDataConfirmationException;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.WizardStep;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.FileManager;
import org.opends.quicksetup.util.IncompatibleVersionException;
import org.opends.quicksetup.util.Utils;

import static org.opends.quicksetup.util.Utils.*;
import static org.opends.quicksetup.Step.*;
import org.opends.server.util.CertificateManager;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.installer.ui.DataOptionsPanel;
import org.opends.quicksetup.installer.ui.DataReplicationPanel;
import org.opends.quicksetup.installer.ui.GlobalAdministratorPanel;
import org.opends.quicksetup.installer.ui.InstallReviewPanel;
import org.opends.quicksetup.installer.ui.InstallWelcomePanel;
import org.opends.quicksetup.installer.ui.InstallLicensePanel;
import org.opends.quicksetup.installer.ui.RemoteReplicationPortsPanel;
import org.opends.quicksetup.installer.ui.RuntimeOptionsPanel;
import org.opends.quicksetup.installer.ui.ServerSettingsPanel;
import org.opends.quicksetup.installer.ui.SuffixesToReplicatePanel;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import javax.naming.ldap.Rdn;
import javax.swing.*;
import org.opends.server.util.DynamicConstants;


/**
 * This is an abstract class that is in charge of actually performing the
 * installation.
 *
 * It just takes a UserData object and based on that installs OpenDJ.
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The
 * notification will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 *
 * Note that we can use freely the class org.opends.server.util.SetupUtils as
 * it is included in quicksetup.jar.
 *
 */
public abstract class Installer extends GuiApplication {
  private TopologyCache lastLoadedCache;

  /** Indicates that we've detected that there is something installed. */
  boolean forceToDisplaySetup = false;

  /** When true indicates that the user has canceled this operation. */
  protected boolean canceled = false;

  private boolean javaVersionCheckFailed;

  /** Map containing information about what has been configured remotely. */
  Map<ServerDescriptor, ConfiguredReplication> hmConfiguredRemoteReplication =
    new HashMap<ServerDescriptor, ConfiguredReplication>();

  // Constants used to do checks
  private static final int MIN_DIRECTORY_MANAGER_PWD = 1;

  private static final Logger LOG = Logger.getLogger(Installer.class.getName());

  /**
   * The minimum integer value that can be used for a port.
   */
  public static final int MIN_PORT_VALUE = 1;

  /**
   * The maximum integer value that can be used for a port.
   */
  public static final int MAX_PORT_VALUE = 65535;

  private static final int MIN_NUMBER_ENTRIES = 1;

  private static final int MAX_NUMBER_ENTRIES = 10000000;

  // If the user decides to import more than this number of entries, the
  // import process of automatically generated data will be verbose.
  private static final int THRESOLD_AUTOMATIC_DATA_VERBOSE = 20000;

  // If the user decides to import a number of entries higher than this
  // threshold, the start process will be verbose.
  private static final int NENTRIES_THRESOLD_FOR_VERBOSE_START = 100000;

  /** Set of progress steps that have been completed. */
  protected Set<InstallProgressStep>
          completedProgress = new HashSet<InstallProgressStep>();

  private final List<WizardStep> lstSteps = new ArrayList<WizardStep>();

  private final HashSet<WizardStep> SUBSTEPS = new HashSet<WizardStep>();
  {
    SUBSTEPS.add(Step.CREATE_GLOBAL_ADMINISTRATOR);
    SUBSTEPS.add(Step.SUFFIXES_OPTIONS);
    SUBSTEPS.add(Step.NEW_SUFFIX_OPTIONS);
    SUBSTEPS.add(Step.REMOTE_REPLICATION_PORTS);
  }

  private final HashMap<WizardStep, WizardStep> hmPreviousSteps =
    new HashMap<WizardStep, WizardStep>();

  private char[] selfSignedCertPw = null;

  private boolean registeredNewServerOnRemote;
  private boolean createdAdministrator;
  private boolean createdRemoteAds;
  private String lastImportProgress;

  /**
   * An static String that contains the class name of ConfigFileHandler.
   */
  protected static final String DEFAULT_CONFIG_CLASS_NAME =
      "org.opends.server.extensions.ConfigFileHandler";

  /** Alias of a self-signed certificate. */
  protected static final String SELF_SIGNED_CERT_ALIAS =
    SecurityOptions.SELF_SIGNED_CERT_ALIAS;

  /** The threshold in minutes used to know whether we must display a warning
   * informing that there is a server clock difference between two servers
   * whose contents are being replicated. */
  public static final int WARNING_CLOCK_DIFFERENCE_THRESOLD_MINUTES = 5;

  /**
   * Creates a default instance.
   */
  public Installer() {
    lstSteps.add(WELCOME);
    if (LicenseFile.exists()) {
        lstSteps.add(LICENSE);
    }
    lstSteps.add(SERVER_SETTINGS);
    lstSteps.add(REPLICATION_OPTIONS);
    lstSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    lstSteps.add(SUFFIXES_OPTIONS);
    lstSteps.add(REMOTE_REPLICATION_PORTS);
    lstSteps.add(NEW_SUFFIX_OPTIONS);
    lstSteps.add(RUNTIME_OPTIONS);
    lstSteps.add(REVIEW);
    lstSteps.add(PROGRESS);
    lstSteps.add(FINISHED);
    try {
      if (!QuickSetupLog.isInitialized())
        QuickSetupLog.initLogFileHandler(
                File.createTempFile(
                    Constants.LOG_FILE_PREFIX,
                    Constants.LOG_FILE_SUFFIX));
    } catch (IOException e) {
      System.err.println("Failed to initialize log");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isCancellable() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UserData createUserData() {
    UserData ud = new UserData();
    ud.setServerLocation(getDefaultServerLocation());
    initializeUserDataWithUserArguments(ud, getUserArguments());
    return ud;
  }

  private void initializeUserDataWithUserArguments(UserData ud,
      String[] userArguments)
  {
    for (int i=0; i<userArguments.length; i++)
    {
      if (userArguments[i].equalsIgnoreCase("--connectTimeout"))
      {
        if (i < userArguments.length - 1)
        {
          String sTimeout = userArguments[i+1];
          try
          {
            ud.setConnectTimeout(new Integer(sTimeout));
          }
          catch (Throwable t)
          {
            LOG.log(Level.WARNING, "Error getting connect timeout: "+t, t);
          }
        }
        break;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void forceToDisplay() {
    forceToDisplaySetup = true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canGoBack(WizardStep step) {
    return step != WELCOME &&
            step != PROGRESS &&
            step != FINISHED;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canGoForward(WizardStep step) {
    return step != REVIEW &&
            step != PROGRESS &&
            step != FINISHED;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canFinish(WizardStep step) {
    return step == REVIEW;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSubStep(WizardStep step)
  {
    return SUBSTEPS.contains(step);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVisible(WizardStep step, UserData userData)
  {
    boolean isVisible;
    if (step == CREATE_GLOBAL_ADMINISTRATOR)
    {
       isVisible = userData.mustCreateAdministrator();
    }
    else if (step == NEW_SUFFIX_OPTIONS)
    {
      SuffixesToReplicateOptions suf =
        userData.getSuffixesToReplicateOptions();
      if (suf != null)
      {
        isVisible = suf.getType() !=
          SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES;
      }
      else
      {
        isVisible = false;
      }
    }
    else if (step == SUFFIXES_OPTIONS)
    {
      DataReplicationOptions repl = userData.getReplicationOptions();
      if (repl != null)
      {
        isVisible =
          (repl.getType() != DataReplicationOptions.Type.STANDALONE) &&
          (repl.getType() != DataReplicationOptions.Type.FIRST_IN_TOPOLOGY);
      }
      else
      {
        isVisible = false;
      }
    }
    else if (step == REMOTE_REPLICATION_PORTS)
    {
      isVisible = isVisible(SUFFIXES_OPTIONS, userData) &&
      (userData.getRemoteWithNoReplicationPort().size() > 0) &&
      (userData.getSuffixesToReplicateOptions().getType() ==
        SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES);
    }
    else
    {
      isVisible = true;
    }
    return isVisible;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVisible(WizardStep step, QuickSetup qs)
  {
    return isVisible(step, getUserData());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean finishClicked(final WizardStep cStep, final QuickSetup qs) {
    if (cStep == Step.REVIEW) {
        updateUserDataForReviewPanel(qs);
        qs.launch();
        qs.setCurrentStep(Step.PROGRESS);
    } else {
        throw new IllegalStateException(
                "Cannot click on finish when we are not in the Review window");
    }
    // Installer responsible for updating the user data and launching
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void nextClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
      throw new IllegalStateException(
          "Cannot click on next from progress step");
    } else if (cStep == REVIEW) {
      throw new IllegalStateException("Cannot click on next from review step");
    } else if (cStep == FINISHED) {
      throw new IllegalStateException(
          "Cannot click on next from finished step");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void closeClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
      if (isFinished()
              || qs.displayConfirmation(INFO_CONFIRM_CLOSE_INSTALL_MSG.get(),
              INFO_CONFIRM_CLOSE_INSTALL_TITLE.get())) {
        qs.quit();
      }
    }
    else if (cStep == FINISHED)
    {
      qs.quit();
    } else {
      throw new IllegalStateException(
              "Close only can be clicked on PROGRESS step");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isFinished()
  {
    return getCurrentProgressStep() == InstallProgressStep.FINISHED_SUCCESSFULLY
            || getCurrentProgressStep() == InstallProgressStep.FINISHED_CANCELED
        || getCurrentProgressStep() == InstallProgressStep.FINISHED_WITH_ERROR;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void cancel() {
    setCurrentProgressStep(InstallProgressStep.WAITING_TO_CANCEL);
    notifyListeners(null);
    this.canceled = true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void quitClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == FINISHED)
    {
      qs.quit();
    }
    else if (cStep == PROGRESS) {
      throw new IllegalStateException(
              "Cannot click on quit from progress step");
    } else if (installStatus.isInstalled()) {
      qs.quit();

    } else if (javaVersionCheckFailed)
    {
      qs.quit();

    } else if (qs.displayConfirmation(INFO_CONFIRM_QUIT_INSTALL_MSG.get(),
            INFO_CONFIRM_QUIT_INSTALL_TITLE.get())) {
      qs.quit();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ButtonName getInitialFocusButtonName() {
    ButtonName name;
    if (!installStatus.isInstalled() || forceToDisplaySetup)
    {
      name = ButtonName.NEXT;
    } else
    {
      if (installStatus.canOverwriteCurrentInstall())
      {
        name = ButtonName.CONTINUE_INSTALL;
      }
      else
      {
        name = ButtonName.QUIT;
      }
    }
    return name;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JPanel createFramePanel(QuickSetupDialog dlg) {
    JPanel p;
    javaVersionCheckFailed = true;
    try
    {
      Utils.checkJavaVersion();
      javaVersionCheckFailed = false;
      if (installStatus.isInstalled() && !forceToDisplaySetup) {
        p = dlg.getInstalledPanel();
      } else {
        p = super.createFramePanel(dlg);
      }
    }
    catch (IncompatibleVersionException ijv)
    {
      MessageBuilder sb = new MessageBuilder();
      sb.append(Utils.breakHtmlString(
          Utils.getHtml(ijv.getMessageObject().toString()),
          Constants.MAX_CHARS_PER_LINE_IN_DIALOG));
      QuickSetupErrorPanel errPanel =
        new QuickSetupErrorPanel(this, sb.toMessage());
      final QuickSetupDialog fDlg = dlg;
      errPanel.addButtonActionListener(
          new ButtonActionListener()
          {
            /**
             * ButtonActionListener implementation. It assumes that we are
             * called in the event thread.
             *
             * @param ev the ButtonEvent we receive.
             */
            @Override
            public void buttonActionPerformed(ButtonEvent ev)
            {
              // Simulate a close button event
              fDlg.notifyButtonEvent(ButtonName.QUIT);
            }
          });
      p = errPanel;
    }
    return p;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<? extends WizardStep> getWizardSteps() {
    return Collections.unmodifiableSet(new HashSet<WizardStep>(lstSteps));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step) {
    QuickSetupStepPanel p = null;
    if (step == WELCOME) {
        p = new InstallWelcomePanel(this);
    } else if (step == LICENSE) {
        p = new InstallLicensePanel(this);
    } else if (step == SERVER_SETTINGS) {
        p = new ServerSettingsPanel(this);
    } else if (step == REPLICATION_OPTIONS) {
      p = new DataReplicationPanel(this);
    } else if (step == CREATE_GLOBAL_ADMINISTRATOR) {
      p = new GlobalAdministratorPanel(this);
    } else if (step == SUFFIXES_OPTIONS) {
      p = new SuffixesToReplicatePanel(this);
    } else if (step == REMOTE_REPLICATION_PORTS) {
      p = new RemoteReplicationPortsPanel(this);
    } else if (step == NEW_SUFFIX_OPTIONS) {
        p = new DataOptionsPanel(this);
    } else if (step == RUNTIME_OPTIONS) {
      p = new RuntimeOptionsPanel(this);
    } else if (step == REVIEW) {
        p = new InstallReviewPanel(this);
    } else if (step == PROGRESS) {
        p = new ProgressPanel(this);
    } else if (step == FINISHED) {
        p = new FinishedPanel(this);
    }
    return p;
  }

  /**
  * {@inheritDoc}
  */
  @Override
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {

    if (installStatus.isInstalled() && forceToDisplaySetup) {
      // Simulate a close button event
      dlg.notifyButtonEvent(ButtonName.QUIT);
    } else {
      if (dlg.getDisplayedStep() == Step.PROGRESS) {
        // Simulate a close button event
        dlg.notifyButtonEvent(ButtonName.CLOSE);
      } else {
        // Simulate a quit button event
        dlg.notifyButtonEvent(ButtonName.QUIT);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getCloseButtonToolTip() {
    return INFO_CLOSE_BUTTON_INSTALL_TOOLTIP.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getQuitButtonToolTip() {
    return INFO_QUIT_BUTTON_INSTALL_TOOLTIP.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getFinishButtonToolTip() {
    return INFO_FINISH_BUTTON_INSTALL_TOOLTIP.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getExtraDialogHeight() {
    return UIFactory.EXTRA_DIALOG_HEIGHT;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void previousClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == WELCOME) {
      throw new IllegalStateException(
          "Cannot click on previous from progress step");
    } else if (cStep == PROGRESS) {
      throw new IllegalStateException(
          "Cannot click on previous from progress step");
    } else if (cStep == FINISHED) {
      throw new IllegalStateException(
          "Cannot click on previous from finished step");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getFrameTitle() {
    return Utils.getCustomizedObject("INFO_FRAME_INSTALL_TITLE",
        INFO_FRAME_INSTALL_TITLE.get(DynamicConstants.PRODUCT_NAME),
        Message.class);
  }

  /** Indicates the current progress step. */
  private InstallProgressStep currentProgressStep =
          InstallProgressStep.NOT_STARTED;

  /**
   * {@inheritDoc}
   */
  @Override
  public void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
    if (!installStatus.isInstalled() || forceToDisplaySetup) {
      // Set the default button for the frame
      if (step == REVIEW) {
        dlg.setFocusOnButton(ButtonName.FINISH);
        dlg.setDefaultButton(ButtonName.FINISH);
      } else if (step == WELCOME) {
        dlg.setDefaultButton(ButtonName.NEXT);
        dlg.setFocusOnButton(ButtonName.NEXT);
      } else if ((step == PROGRESS) || (step == FINISHED)) {
        dlg.setDefaultButton(ButtonName.CLOSE);
        dlg.setFocusOnButton(ButtonName.CLOSE);
      } else {
        dlg.setDefaultButton(ButtonName.NEXT);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ProgressStep getCurrentProgressStep()
  {
    return currentProgressStep;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WizardStep getFirstWizardStep() {
    return WELCOME;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WizardStep getNextWizardStep(WizardStep step) {
    WizardStep next = null;
    if (step == Step.REPLICATION_OPTIONS)
    {
      if (getUserData().mustCreateAdministrator())
      {
        next = Step.CREATE_GLOBAL_ADMINISTRATOR;
      }
      else
      {
        switch (getUserData().getReplicationOptions().getType())
        {
        case FIRST_IN_TOPOLOGY:
          next = Step.NEW_SUFFIX_OPTIONS;
          break;
        case STANDALONE:
          next = Step.NEW_SUFFIX_OPTIONS;
          break;
        default:
          next = Step.SUFFIXES_OPTIONS;
        }
      }
    }
    else if (step == Step.SUFFIXES_OPTIONS)
    {
      switch (getUserData().getSuffixesToReplicateOptions().
          getType())
      {
      case REPLICATE_WITH_EXISTING_SUFFIXES:

        if (getUserData().getRemoteWithNoReplicationPort().size() > 0)
        {
          next = Step.REMOTE_REPLICATION_PORTS;
        }
        else
        {
          next = Step.RUNTIME_OPTIONS;
        }
        break;
      default:
        next = Step.NEW_SUFFIX_OPTIONS;
      }
    }
    else if (step == Step.REMOTE_REPLICATION_PORTS)
    {
      next = Step.RUNTIME_OPTIONS;
    }
    else
    {
      int i = lstSteps.indexOf(step);
      if (i != -1 && i + 1 < lstSteps.size()) {
        next = lstSteps.get(i + 1);
      }
    }
    if (next != null)
    {
      hmPreviousSteps.put(next, step);
    }
    return next;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LinkedHashSet<WizardStep> getOrderedSteps()
  {
    LinkedHashSet<WizardStep> orderedSteps = new LinkedHashSet<WizardStep>();
    orderedSteps.add(WELCOME);
    if (lstSteps.contains(LICENSE)) {
       orderedSteps.add(LICENSE);
    }
    orderedSteps.add(SERVER_SETTINGS);
    orderedSteps.add(REPLICATION_OPTIONS);
    orderedSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    orderedSteps.add(SUFFIXES_OPTIONS);
    orderedSteps.add(REMOTE_REPLICATION_PORTS);
    orderedSteps.add(NEW_SUFFIX_OPTIONS);
    orderedSteps.add(RUNTIME_OPTIONS);
    orderedSteps.add(REVIEW);
    orderedSteps.add(PROGRESS);
    orderedSteps.add(FINISHED);
    return orderedSteps;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WizardStep getPreviousWizardStep(WizardStep step) {
    //  Try with the steps calculated in method getNextWizardStep.
    WizardStep prev = hmPreviousSteps.get(step);

    if (prev == null)
    {
      int i = lstSteps.indexOf(step);
      if (i != -1 && i > 0) {
        prev = lstSteps.get(i - 1);
      }
    }
    return prev;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WizardStep getFinishedStep() {
    return Step.FINISHED;
  }

  /**
   * Uninstalls installed services.  This is to be used when the user
   * has elected to cancel an installation.
   */
  protected void uninstallServices() {
    if (completedProgress.contains(
            InstallProgressStep.ENABLING_WINDOWS_SERVICE)) {
      try {
        new InstallerHelper().disableWindowsService();
      } catch (ApplicationException ae) {
        LOG.log(Level.INFO, "Error disabling Windows service", ae);
      }
    }

    unconfigureRemote();
  }

  /**
   * Creates the template files based in the contents of the UserData object.
   * These templates files are used to generate automatically data.  To generate
   * the template file the code will basically take into account the value of
   * the base dn and the number of entries to be generated.
   *
   * @return a list of file objects pointing to the create template files.
   * @throws ApplicationException if an error occurs.
   */
  private File createTemplateFile() throws ApplicationException {
    File file;
    try
    {
      Set<String> baseDNs = new LinkedHashSet<String>(
          getUserData().getNewSuffixOptions().getBaseDns());
      int nEntries = getUserData().getNewSuffixOptions().getNumberEntries();
      file = SetupUtils.createTemplateFile(baseDNs, nEntries);
    }
    catch (IOException ioe)
    {
      Message failedMsg = getThrowableMsg(
          INFO_ERROR_CREATING_TEMP_FILE.get(), ioe);
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
          failedMsg, ioe);
    }
    return file;
  }

  /**
   * This methods configures the server based on the contents of the UserData
   * object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  protected void configureServer() throws ApplicationException {
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CONFIGURING.get()));

    if (Utils.isWebStart())
    {
      String installDir = getUserData().getServerLocation();
      setInstallation(new Installation(installDir, installDir));
    }

    copyTemplateInstance();

    writeOpenDSJavaHome();

    writeHostName();

    checkAbort();

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(getConfigurationClassName());

    argList.add("-c");
    argList.add(getConfigurationFile());
    argList.add("-h");
    argList.add(String.valueOf(getUserData().getHostName()));
    argList.add("-p");
    argList.add(String.valueOf(getUserData().getServerPort()));
    argList.add("--adminConnectorPort");
    argList.add(String.valueOf(getUserData().getAdminConnectorPort()));

    SecurityOptions sec = getUserData().getSecurityOptions();
    // TODO: even if the user does not configure SSL maybe we should choose
    // a secure port that is not being used and that we can actually use.
    if (sec.getEnableSSL())
    {
      argList.add("-P");
      argList.add(String.valueOf(sec.getSslPort()));
    }

    if (sec.getEnableStartTLS())
    {
      argList.add("-q");
    }

    String aliasInKeyStore = sec.getAliasToUse();
    String aliasInTrustStore;
    if (aliasInKeyStore == null)
    {
      aliasInTrustStore = SELF_SIGNED_CERT_ALIAS;
    }
    else
    {
      aliasInTrustStore = aliasInKeyStore;
    }

    switch (sec.getCertificateType())
    {
    case SELF_SIGNED_CERTIFICATE:
      argList.add("-k");
      argList.add("cn=JKS,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      break;
    case JKS:
      argList.add("-k");
      argList.add("cn=JKS,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      argList.add("-m");
      argList.add(sec.getKeystorePath());
      if (aliasInKeyStore != null)
      {
        argList.add("-a");
        argList.add(aliasInKeyStore);
      }
      break;
    case JCEKS:
      argList.add("-k");
      argList.add("cn=JCEKS,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      argList.add("cn=JCEKS,cn=Trust Manager Providers,cn=config");
      argList.add("-m");
      argList.add(sec.getKeystorePath());
      if (aliasInKeyStore != null)
      {
        argList.add("-a");
        argList.add(aliasInKeyStore);
      }
      break;
    case PKCS12:
      argList.add("-k");
      argList.add("cn=PKCS12,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      // We are going to import the PCKS12 certificate in a JKS trust store
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      argList.add("-m");
      argList.add(sec.getKeystorePath());
      if (aliasInKeyStore != null)
      {
        argList.add("-a");
        argList.add(aliasInKeyStore);
      }
      break;
    case PKCS11:
      argList.add("-k");
      argList.add("cn=PKCS11,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      // We are going to import the PCKS11 certificate in a JKS trust store
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      if (aliasInKeyStore != null)
      {
        argList.add("-a");
        argList.add(aliasInKeyStore);
      }
      break;
    case NO_CERTIFICATE:
      // Nothing to do.
      break;
    default:
      throw new IllegalStateException("Unknown certificate type: "+
          sec.getCertificateType());
    }

    // For the moment do not enable JMX
    if (getUserData().getServerJMXPort() > 0)
    {
      argList.add("-x");
      argList.add(String.valueOf(getUserData().getServerJMXPort()));
    }

    argList.add("-D");
    argList.add(getUserData().getDirectoryManagerDn());

    argList.add("-w");
    argList.add(getUserData().getDirectoryManagerPwd());

    if (createNotReplicatedSuffix())
    {
      LinkedList<String> baseDns =
        getUserData().getNewSuffixOptions().getBaseDns();
      for (String baseDn : baseDns)
      {
        argList.add("-b");
        argList.add(baseDn);
      }
    }

    argList.add("-R");
    argList.add(getInstallation().getRootDirectory().getAbsolutePath());

    final String[] args = new String[argList.size()];
    argList.toArray(args);
    StringBuilder cmd = new StringBuilder();
    boolean nextPassword = false;
    for (String s : argList)
    {
      if (cmd.length() > 0)
      {
        cmd.append(" ");
      }
      if (nextPassword)
      {
        cmd.append("{rootUserPassword}");
      }
      else
      {
        cmd.append(s);
      }
      nextPassword = "-w".equals(s);
    }
    LOG.log(Level.INFO, "configure DS cmd: "+cmd);
    final InstallerHelper helper = new InstallerHelper();
    setNotifyListeners(false);
    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          if (helper.invokeConfigureServer(args) != 0)
          {
            ae = new ApplicationException(
                ReturnCode.CONFIGURATION_ERROR,
                INFO_ERROR_CONFIGURING.get(), null);
          }
          else
          {
            if (getUserData().getNewSuffixOptions().getBaseDns().isEmpty())
            {
              helper.deleteBackend(getBackendName());
            }
          }
        } catch (ApplicationException aex)
        {
          ae = aex;
        } catch (Throwable t)
        {
          ae = new ApplicationException(
              ReturnCode.CONFIGURATION_ERROR,
              getThrowableMsg(INFO_ERROR_CONFIGURING.get(), t), t);
        }
        finally
        {
          setNotifyListeners(true);
        }
        isOver = true;
      }
      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    invokeLongOperation(thread);
    notifyListeners(getFormattedDoneWithLineBreak());
    checkAbort();

    try
    {
      SecurityOptions.CertificateType certType = sec.getCertificateType();
      if (certType != SecurityOptions.CertificateType.NO_CERTIFICATE)
      {
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_UPDATING_CERTIFICATES.get()));
      }
      CertificateManager certManager;
      CertificateManager trustManager;
      File f;
      switch (certType)
      {
      case NO_CERTIFICATE:
        // Nothing to do
        break;
      case SELF_SIGNED_CERTIFICATE:
        String pwd = getSelfSignedCertificatePwd();
        certManager = new CertificateManager(
            getSelfSignedKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            pwd);
        certManager.generateSelfSignedCertificate(SELF_SIGNED_CERT_ALIAS,
            getSelfSignedCertificateSubjectDN(),
            getSelfSignedCertificateValidity());
        SetupUtils.exportCertificate(certManager, SELF_SIGNED_CERT_ALIAS,
            getTemporaryCertificatePath());

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            pwd);
        trustManager.addCertificate(SELF_SIGNED_CERT_ALIAS,
            new File(getTemporaryCertificatePath()));
        createProtectedFile(getKeystorePinPath(), pwd);
        f = new File(getTemporaryCertificatePath());
        f.delete();

        break;
      case JKS:
        certManager = new CertificateManager(
            sec.getKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        if (aliasInKeyStore != null)
        {
          SetupUtils.exportCertificate(certManager, aliasInKeyStore,
              getTemporaryCertificatePath());
        }
        else
        {
          SetupUtils.exportCertificate(certManager,
              getTemporaryCertificatePath());
        }

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        trustManager.addCertificate(aliasInTrustStore,
            new File(getTemporaryCertificatePath()));
        createProtectedFile(getKeystorePinPath(), sec.getKeystorePassword());
        f = new File(getTemporaryCertificatePath());
        f.delete();
        break;
      case JCEKS:
        certManager = new CertificateManager(
            sec.getKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_JCEKS,
            sec.getKeystorePassword());
        if (aliasInKeyStore != null)
        {
          SetupUtils.exportCertificate(certManager, aliasInKeyStore,
              getTemporaryCertificatePath());
        }
        else
        {
          SetupUtils.exportCertificate(certManager,
              getTemporaryCertificatePath());
        }

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JCEKS,
            sec.getKeystorePassword());
        trustManager.addCertificate(aliasInTrustStore,
            new File(getTemporaryCertificatePath()));
        createProtectedFile(getKeystorePinPath(), sec.getKeystorePassword());
        f = new File(getTemporaryCertificatePath());
        f.delete();
        break;
      case PKCS12:
        certManager = new CertificateManager(
            sec.getKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_PKCS12,
            sec.getKeystorePassword());
        if (aliasInKeyStore != null)
        {
          SetupUtils.exportCertificate(certManager, aliasInKeyStore,
              getTemporaryCertificatePath());
        }
        else
        {
          SetupUtils.exportCertificate(certManager,
              getTemporaryCertificatePath());
        }

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        trustManager.addCertificate(aliasInTrustStore,
            new File(getTemporaryCertificatePath()));
        createProtectedFile(getKeystorePinPath(), sec.getKeystorePassword());
        f = new File(getTemporaryCertificatePath());
        f.delete();
        break;
      case PKCS11:
        certManager = new CertificateManager(
            CertificateManager.KEY_STORE_PATH_PKCS11,
            CertificateManager.KEY_STORE_TYPE_PKCS11,
            sec.getKeystorePassword());
        if (aliasInKeyStore != null)
        {
          SetupUtils.exportCertificate(certManager, aliasInKeyStore,
              getTemporaryCertificatePath());
        }
        else
        {
          SetupUtils.exportCertificate(certManager,
              getTemporaryCertificatePath());
        }

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        trustManager.addCertificate(aliasInTrustStore,
            new File(getTemporaryCertificatePath()));
        createProtectedFile(getKeystorePinPath(), sec.getKeystorePassword());
        break;
      default:
        throw new IllegalStateException("Unknown certificate type: "+certType);
      }
      if (certType != SecurityOptions.CertificateType.NO_CERTIFICATE)
      {
        notifyListeners(getFormattedDoneWithLineBreak());
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.SEVERE, "Error configuring certificate: "+t, t);
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR,
          getThrowableMsg(INFO_ERROR_CONFIGURING_CERTIFICATE.get(),
                  t), t);
    }
  }

  /**
   * This methods creates the base entry for the suffix based on the contents of
   * the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void createBaseEntry() throws ApplicationException {
    LinkedList<String> baseDns =
      getUserData().getNewSuffixOptions().getBaseDns();
    if (baseDns.size() == 1)
    {
      notifyListeners(getFormattedWithPoints(
        INFO_PROGRESS_CREATING_BASE_ENTRY.get(baseDns.getFirst())));
    }
    else
    {
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_CREATING_BASE_ENTRIES.get()));
    }

    final InstallerHelper helper = new InstallerHelper();

    LinkedList<File> ldifFiles = new LinkedList<File>();

    for (String baseDn : baseDns)
    {
      ldifFiles.add(helper.createBaseEntryTempFile(baseDn));
    }
    checkAbort();

    ArrayList<String> argList = new ArrayList<String>();

    argList.add("-n");
    argList.add(getBackendName());

    for (File f : ldifFiles)
    {
      argList.add("-l");
      argList.add(f.getAbsolutePath());
    }

    argList.add("-F");

    argList.add("-Q");

    argList.add("--noPropertiesFile");

    final String[] args = new String[argList.size()];
    argList.toArray(args);

    setNotifyListeners(false);

    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          int result = helper.invokeImportLDIF(Installer.this, args);

          if (result != 0)
          {
            ae = new ApplicationException(
                ReturnCode.IMPORT_ERROR,
                INFO_ERROR_CREATING_BASE_ENTRY.get(), null);
          }
        } catch (Throwable t)
        {
          ae = new ApplicationException(
              ReturnCode.IMPORT_ERROR,
              getThrowableMsg(INFO_ERROR_CREATING_BASE_ENTRY.get(), t), t);
        }
        finally
        {
          setNotifyListeners(true);
        }
        isOver = true;
      }
      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    invokeLongOperation(thread);
    notifyListeners(getFormattedDoneWithLineBreak());
  }

  /**
   * This methods imports the contents of an LDIF file based on the contents of
   * the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void importLDIF() throws ApplicationException {
    LinkedList<String> ldifPaths =
      getUserData().getNewSuffixOptions().getLDIFPaths();
    MessageBuilder mb = new MessageBuilder();
    if (ldifPaths.size() > 1)
    {
      if (isVerbose())
      {
        mb.append(getFormattedProgress(INFO_PROGRESS_IMPORTING_LDIFS.get(
            getStringFromCollection(ldifPaths, ", "))));
        mb.append(getLineBreak());
      }
      else
      {
        mb.append(getFormattedProgress(
            INFO_PROGRESS_IMPORTING_LDIFS_NON_VERBOSE.get(
            getStringFromCollection(ldifPaths, ", "))));
      }
    }
    else
    {
      if (isVerbose())
      {
        mb.append(getFormattedProgress(INFO_PROGRESS_IMPORTING_LDIF.get(
          ldifPaths.getFirst())));
        mb.append(getLineBreak());
      }
      else
      {
        mb.append(getFormattedProgress(
                INFO_PROGRESS_IMPORTING_LDIF_NON_VERBOSE.get(
                ldifPaths.getFirst())));
      }
    }
    notifyListeners(mb.toMessage());

    final PointAdder pointAdder = new PointAdder();

    if (!isVerbose())
    {
      setNotifyListeners(false);
      pointAdder.start();
    }

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-n");
    argList.add(getBackendName());
    for (String ldifPath : ldifPaths)
    {
      argList.add("-l");
      argList.add(ldifPath);
    }
    argList.add("-F");
    String rejectedFile = getUserData().getNewSuffixOptions().getRejectedFile();
    if (rejectedFile != null)
    {
      argList.add("-R");
      argList.add(rejectedFile);
    }
    String skippedFile = getUserData().getNewSuffixOptions().getSkippedFile();
    if (skippedFile != null)
    {
      argList.add("--skipFile");
      argList.add(skippedFile);
    }

    argList.add("--noPropertiesFile");

    final String[] args = new String[argList.size()];
    argList.toArray(args);

    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          InstallerHelper helper = new InstallerHelper();
          int result = helper.invokeImportLDIF(Installer.this, args);

          if (result != 0)
          {
            ae = new ApplicationException(
                ReturnCode.IMPORT_ERROR,
                INFO_ERROR_IMPORTING_LDIF.get(), null);
          }
        } catch (Throwable t)
        {
          ae = new ApplicationException(
              ReturnCode.IMPORT_ERROR,
              getThrowableMsg(INFO_ERROR_IMPORTING_LDIF.get(), t), t);
        }
        finally
        {
          if (!isVerbose())
          {
            setNotifyListeners(true);
            pointAdder.stop();
          }
        }
        isOver = true;
      }
      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    try
    {
      invokeLongOperation(thread);
    } catch (ApplicationException ae)
    {
      if (!isVerbose())
      {
        if (lastImportProgress != null)
        {
          notifyListeners(
              getFormattedProgress(Message.raw(lastImportProgress)));
          notifyListeners(getLineBreak());
        }
      }
      throw ae;
    }
    if (!isVerbose())
    {
      if (lastImportProgress == null)
      {
        notifyListeners(getFormattedDoneWithLineBreak());
      }
      else
      {
        notifyListeners(
            getFormattedProgress(Message.raw(lastImportProgress)));
        notifyListeners(getLineBreak());
      }
    }
  }

  /**
   * This methods imports automatically generated data based on the contents
   * of the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void importAutomaticallyGenerated() throws ApplicationException {
    File templatePath = createTemplateFile();
    int nEntries = getUserData().getNewSuffixOptions().getNumberEntries();
    MessageBuilder mb = new MessageBuilder();
    if (isVerbose() || (nEntries > THRESOLD_AUTOMATIC_DATA_VERBOSE))
    {
      mb.append(getFormattedProgress(
            INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED.get(
                    String.valueOf(nEntries))));
      mb.append(getLineBreak());
    }
    else
    {
      mb.append(getFormattedProgress(
          INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED_NON_VERBOSE.get(
                  String.valueOf(nEntries))));
    }
    notifyListeners(mb.toMessage());

    final PointAdder pointAdder = new PointAdder();
    if (!isVerbose())
    {
      pointAdder.start();
    }

    if (!isVerbose())
    {
      setNotifyListeners(false);
    }
    final ArrayList<String> argList = new ArrayList<String>();
    argList.add("-n");
    argList.add(getBackendName());
    argList.add("-A");
    argList.add(templatePath.getAbsolutePath());
    argList.add("-s"); // seed
    argList.add("0");

    argList.add("-F");

    argList.add("--noPropertiesFile");

    final String[] args = new String[argList.size()];
    argList.toArray(args);

    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          InstallerHelper helper = new InstallerHelper();
          int result = helper.invokeImportLDIF(Installer.this, args);

          if (result != 0)
          {
            ae = new ApplicationException(
                ReturnCode.IMPORT_ERROR,
                INFO_ERROR_IMPORT_LDIF_TOOL_RETURN_CODE.get(
                    Integer.toString(result)), null);
          }
        } catch (Throwable t)
        {
          ae = new ApplicationException(
              ReturnCode.IMPORT_ERROR,
              getThrowableMsg(INFO_ERROR_IMPORT_AUTOMATICALLY_GENERATED.get(
                  listToString(argList, " "), t.getLocalizedMessage()), t),
                  t);
        }
        finally
        {
          if (!isVerbose())
          {
            setNotifyListeners(true);
            if (ae != null)
            {
              pointAdder.stop();
            }
          }
        }
        isOver = true;
      }
      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    invokeLongOperation(thread);
    if (!isVerbose())
    {
      pointAdder.stop();
      notifyListeners(getFormattedDoneWithLineBreak());
    }
  }

  /**
   * This method undoes the modifications made in other servers in terms of
   * replication.  This method assumes that we are aborting the Installer and
   * that is why it does not call checkAbort.
   */
  private void unconfigureRemote()
  {
    InitialLdapContext ctx = null;
    if (registeredNewServerOnRemote || createdAdministrator ||
            createdRemoteAds)
    {
      // Try to connect
      DataReplicationOptions repl = getUserData().getReplicationOptions();
      AuthenticationData auth = repl.getAuthenticationData();
      String ldapUrl = getLdapUrl(auth);
      String dn = auth.getDn();
      String pwd = auth.getPwd();
      if (isVerbose())
      {
        notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_UNCONFIGURING_ADS_ON_REMOTE.get(getHostDisplay(auth))));
      }
      try
      {
        if (auth.useSecureConnection())
        {
          ApplicationTrustManager trustManager = getTrustManager();
          trustManager.setHost(auth.getHostName());
          ctx = createLdapsContext(ldapUrl, dn, pwd,
              getConnectTimeout(), null, trustManager);
        }
        else
        {
          ctx = createLdapContext(ldapUrl, dn, pwd,
              getConnectTimeout(), null);
        }

        ADSContext adsContext = new ADSContext(ctx);
        if (createdRemoteAds)
        {
          adsContext.removeAdminData(true);
        }
        else
        {
          if (registeredNewServerOnRemote)
          {
            try
            {
              adsContext.unregisterServer(getNewServerAdsProperties(
                                                                getUserData()));
            }
            catch (ADSContextException ace)
            {
              if (ace.getError() !=
                ADSContextException.ErrorType.NOT_YET_REGISTERED)
              {
                throw ace;
              }
              // Else, nothing to do: this may occur if the new server has been
              // unregistered on another server and the modification has been
              // already propagated by replication.
            }
          }
          if (createdAdministrator)
          {
            adsContext.deleteAdministrator(getAdministratorProperties(
                                                                getUserData()));
          }
        }
        if (isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
      }
      catch (Throwable t)
      {
        notifyListeners(getFormattedError(t, true));
      }
      finally
      {
        StaticUtils.close(ctx);
      }
    }
    InstallerHelper helper = new InstallerHelper();
    for (ServerDescriptor server : hmConfiguredRemoteReplication.keySet())
    {
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_UNCONFIGURING_REPLICATION_REMOTE.get(
                  getHostPort(server))));
      try
      {
        ctx = getRemoteConnection(server, getTrustManager(),
            getPreferredConnections());
        helper.unconfigureReplication(ctx,
            hmConfiguredRemoteReplication.get(server),
            ConnectionUtils.getHostPort(ctx));
      }
      catch (ApplicationException ae)
      {
        notifyListeners(getFormattedError(ae, true));
      }
      StaticUtils.close(ctx);
      notifyListeners(getFormattedDoneWithLineBreak());
    }
  }

  /**
   * This method configures the backends and suffixes that must be replicated.
   * The setup uses the same backend names as in the remote servers.  If
   * userRoot is not one of the backends defined in the remote servers, it
   * deletes it from the configuration.
   * NOTE: this method assumes that the server is running.
   * @throws ApplicationException if something goes wrong.
   */
  protected void createReplicatedBackendsIfRequired()
  throws ApplicationException
  {
    if (getUserData().getReplicationOptions().getType()
        == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY &&
        getUserData().getNewSuffixOptions().getBaseDns().isEmpty())
    {
      // There is nothing to do.
      return;
    }
    notifyListeners(getFormattedWithPoints(
        INFO_PROGRESS_CREATING_REPLICATED_BACKENDS.get()));
    // The keys are the backend IDs and the values the list of base DNs.
    Map<String, Set<String>> hmBackendSuffix =
      new HashMap<String, Set<String>>();
    boolean deleteUserRoot = false;
    if (getUserData().getReplicationOptions().getType()
        == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY)
    {
      Set<String> baseDns = new HashSet<String>(
        getUserData().getNewSuffixOptions().getBaseDns());
      hmBackendSuffix.put(getBackendName(), baseDns);
    }
    else
    {
      Set<SuffixDescriptor> suffixes =
        getUserData().getSuffixesToReplicateOptions().getSuffixes();

      // The criteria to choose the name of the backend is to try to have the
      // configuration of the other server.  The algorithm consists on putting
      // the remote servers in a list and pick the backend as they appear on the
      // list.
      LinkedHashSet<ServerDescriptor> serverList =
        new LinkedHashSet<ServerDescriptor>();
      for (SuffixDescriptor suffix : suffixes)
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          serverList.add(replica.getServer());
        }
      }

      for (SuffixDescriptor suffix : suffixes)
      {
        String backendName = null;
        for (ServerDescriptor server : serverList)
        {
          for (ReplicaDescriptor replica : suffix.getReplicas())
          {
            if (replica.getServer() == server)
            {
              backendName = replica.getBackendName();
              break;
            }
          }
          if (backendName != null)
          {
            break;
          }
        }
        boolean found = false;
        for (String storedBackend : hmBackendSuffix.keySet())
        {
          if (storedBackend.equalsIgnoreCase(backendName))
          {
            found = true;
            hmBackendSuffix.get(storedBackend).add(suffix.getDN());
            break;
          }
        }
        if (!found)
        {
          Set<String> baseDns = new HashSet<String>();
          baseDns.add(suffix.getDN());
          hmBackendSuffix.put(backendName, baseDns);
        }
      }
      deleteUserRoot = true;
      for (String backendName : hmBackendSuffix.keySet())
      {
        if (backendName.equalsIgnoreCase(getBackendName()))
        {
          deleteUserRoot = false;
          break;
        }
      }
    }

    InstallerHelper helper = new InstallerHelper();

    InitialLdapContext ctx = null;
    try
    {
      ctx = createLocalContext();
      if (deleteUserRoot)
      {
        // Delete the userRoot backend.
        helper.deleteBackend(ctx, getBackendName(),
            ConnectionUtils.getHostPort(ctx));
      }
      for (String backendName : hmBackendSuffix.keySet())
      {
        if (backendName.equalsIgnoreCase(getBackendName()))
        {
          helper.setBaseDns(
              ctx, backendName, hmBackendSuffix.get(backendName),
              ConnectionUtils.getHostPort(ctx));
        }
        else
        {
          helper.createLocalDBBackend(
              ctx, backendName, hmBackendSuffix.get(backendName),
              ConnectionUtils.getHostPort(ctx));
        }
      }
    }
    catch (ApplicationException ae)
    {
      throw ae;
    }
    catch (NamingException ne)
    {
      Message failedMsg = getThrowableMsg(
              INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne);
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR, failedMsg, ne);
    }
    finally
    {
      StaticUtils.close(ctx);
    }

    notifyListeners(getFormattedDoneWithLineBreak());
    checkAbort();
  }

  /**
   * This method creates the replication configuration for the suffixes on the
   * the local server (and eventually in the remote servers) to synchronize
   * things.
   * NOTE: this method assumes that the server is running.
   * @throws ApplicationException if something goes wrong.
   */
  protected void configureReplication() throws ApplicationException
  {
    notifyListeners(getFormattedWithPoints(
        INFO_PROGRESS_CONFIGURING_REPLICATION.get()));

    InstallerHelper helper = new InstallerHelper();
    Set<Integer> knownServerIds = new HashSet<Integer>();
    Set<Integer> knownReplicationServerIds = new HashSet<Integer>();
    if (lastLoadedCache != null)
    {
      for (SuffixDescriptor suffix : lastLoadedCache.getSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          knownServerIds.add(replica.getReplicationId());
        }
      }
      for (ServerDescriptor server : lastLoadedCache.getServers())
      {
        Object v = server.getServerProperties().get
        (ServerDescriptor.ServerProperty.REPLICATION_SERVER_ID);
        if (v != null)
        {
          knownReplicationServerIds.add((Integer)v);
        }
      }
    }
    else
    {
      /* There is no ADS anywhere.  Just use the SuffixDescriptors we found */
      for (SuffixDescriptor suffix :
        getUserData().getSuffixesToReplicateOptions().getAvailableSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          knownServerIds.add(replica.getReplicationId());
          Object v = replica.getServer().getServerProperties().get
          (ServerDescriptor.ServerProperty.REPLICATION_SERVER_ID);
          if (v != null)
          {
            knownReplicationServerIds.add((Integer)v);
          }
        }
      }
    }

    /* For each suffix specified by the user, create a map from the suffix
       DN to the set of replication servers. The initial instance in a topology
       is a degenerate case. Also, collect a set of all observed replication
       servers as the set of ADS suffix replicas (all instances hosting the
       replication server also replicate ADS). */
    Map<String, Set<String>> replicationServers
            = new HashMap<String, Set<String>>();
    HashSet<String> adsServers = new HashSet<String>();

    if (getUserData().getReplicationOptions().getType()
            == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY)
    {
      LinkedList<String> baseDns =
        getUserData().getNewSuffixOptions().getBaseDns();
      HashSet<String> h = new HashSet<String>();
      h.add(getLocalReplicationServer());
      adsServers.add(getLocalReplicationServer());
      for (String dn : baseDns)
      {
        replicationServers.put(dn, new HashSet<String>(h));
      }
    }
    else
    {
      Set<SuffixDescriptor> suffixes =
        getUserData().getSuffixesToReplicateOptions().getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        HashSet<String> h = new HashSet<String>();
        h.addAll(suffix.getReplicationServers());
        adsServers.addAll(suffix.getReplicationServers());
        h.add(getLocalReplicationServer());
        adsServers.add(getLocalReplicationServer());
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          ServerDescriptor server = replica.getServer();
          AuthenticationData repPort
                  = getUserData().getRemoteWithNoReplicationPort().get(server);
          if (repPort != null)
          {
            h.add(server.getHostName()+":"+repPort.getPort());
            adsServers.add(server.getHostName()+":"+repPort.getPort());
          }
        }
        replicationServers.put(suffix.getDN(), h);
      }
    }
    replicationServers.put(ADSContext.getAdministrationSuffixDN(), adsServers);
    replicationServers.put(Constants.SCHEMA_DN,
        new HashSet<String>(adsServers));

    InitialLdapContext ctx = null;
    long localTime = -1;
    long localTimeMeasureTime = -1;
    String localServerDisplay = null;
    try
    {
      ctx = createLocalContext();
      helper.configureReplication(ctx, replicationServers,
          getUserData().getReplicationOptions().getReplicationPort(),
          getUserData().getReplicationOptions().useSecureReplication(),
          getLocalHostPort(),
          knownReplicationServerIds, knownServerIds);
      localTimeMeasureTime = System.currentTimeMillis();
      localTime = Utils.getServerClock(ctx);
      localServerDisplay = ConnectionUtils.getHostPort(ctx);
    }
    catch (ApplicationException ae)
    {
      throw ae;
    }
    catch (NamingException ne)
    {
      Message failedMsg = getThrowableMsg(
              INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne);
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR, failedMsg, ne);
    }
    finally
    {
      StaticUtils.close(ctx);
    }
    notifyListeners(getFormattedDoneWithLineBreak());
    checkAbort();

    if (getUserData().getReplicationOptions().getType()
            == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      Map<ServerDescriptor, Set<ReplicaDescriptor>> hm =
        new HashMap<ServerDescriptor, Set<ReplicaDescriptor>>();
      for (SuffixDescriptor suffix :
        getUserData().getSuffixesToReplicateOptions().getSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          Set<ReplicaDescriptor> replicas = hm.get(replica.getServer());
          if (replicas == null)
          {
            replicas = new HashSet<ReplicaDescriptor>();
            hm.put(replica.getServer(), replicas);
          }
          replicas.add(replica);
        }
      }
      for (ServerDescriptor server : hm.keySet())
      {
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_CONFIGURING_REPLICATION_REMOTE.get(
                    getHostPort(server))));
        Integer v = (Integer)server.getServerProperties().get(
            ServerDescriptor.ServerProperty.REPLICATION_SERVER_PORT);
        int replicationPort;
        boolean enableSecureReplication;
        if (v != null)
        {
          replicationPort = v;
          enableSecureReplication = false;
        }
        else
        {
          AuthenticationData authData =
            getUserData().getRemoteWithNoReplicationPort().get(server);
          if (authData != null)
          {
            replicationPort = authData.getPort();
            enableSecureReplication = authData.useSecureConnection();
          }
          else
          {
            replicationPort = Constants.DEFAULT_REPLICATION_PORT;
            enableSecureReplication = false;
            LOG.log(Level.WARNING, "Could not find replication port for: "+
                getHostPort(server));
          }
        }
        HashSet<String> dns = new HashSet<String>();
        for (ReplicaDescriptor replica : hm.get(server))
        {
          dns.add(replica.getSuffix().getDN());
        }
        dns.add(ADSContext.getAdministrationSuffixDN());
        dns.add(Constants.SCHEMA_DN);
        Map<String, Set<String>> remoteReplicationServers
        = new HashMap<String, Set<String>>();
        for (String dn : dns)
        {
          Set<String> repServer = replicationServers.get(dn);
          if (repServer == null)
          {
            // Do the comparison manually
            for (String dn1 : replicationServers.keySet())
            {
              if (Utils.areDnsEqual(dn, dn1))
              {
                repServer = replicationServers.get(dn1);
                dn = dn1;
                break;
              }
            }
          }
          if (repServer != null)
          {
            remoteReplicationServers.put(dn, repServer);
          }
          else
          {
            LOG.log(Level.WARNING, "Could not find replication server for: "+
                dn);
          }
        }


        ctx = getRemoteConnection(server, getTrustManager(),
            getPreferredConnections());
        ConfiguredReplication repl =
          helper.configureReplication(ctx, remoteReplicationServers,
              replicationPort, enableSecureReplication,
              ConnectionUtils.getHostPort(ctx), knownReplicationServerIds,
              knownServerIds);
        long remoteTimeMeasureTime = System.currentTimeMillis();
        long remoteTime = Utils.getServerClock(ctx);
        if ((localTime != -1) && (remoteTime != -1))
        {
          if (Math.abs(localTime - remoteTime - localTimeMeasureTime +
              remoteTimeMeasureTime) >
          (WARNING_CLOCK_DIFFERENCE_THRESOLD_MINUTES * 60 * 1000))
          {
            notifyListeners(getFormattedWarning(
                INFO_WARNING_SERVERS_CLOCK_DIFFERENCE.get(
                    localServerDisplay, ConnectionUtils.getHostPort(ctx),
                    String.valueOf(
                        WARNING_CLOCK_DIFFERENCE_THRESOLD_MINUTES))));
          }
        }

        hmConfiguredRemoteReplication.put(server, repl);

        StaticUtils.close(ctx);
        notifyListeners(getFormattedDoneWithLineBreak());
        checkAbort();
      }
    }
  }

  /**
   * This methods enables this server as a Windows service.
   * @throws ApplicationException if something goes wrong.
   */
  protected void enableWindowsService() throws ApplicationException {
      notifyListeners(getFormattedWithPoints(
        INFO_PROGRESS_ENABLING_WINDOWS_SERVICE.get()));
      InstallerHelper helper = new InstallerHelper();
      helper.enableWindowsService();
      notifyListeners(getLineBreak());
  }

  /**
   * Updates the contents of the provided map with the localized summary
   * strings.
   * @param hmSummary the Map to be updated.
   * @param isCli a boolean to indicate if the install is using CLI or GUI
   */
  protected void initSummaryMap(
      Map<InstallProgressStep, Message> hmSummary,
      boolean isCli)
  {
    hmSummary.put(InstallProgressStep.NOT_STARTED,
        getFormattedSummary(INFO_SUMMARY_INSTALL_NOT_STARTED.get()));
    hmSummary.put(InstallProgressStep.DOWNLOADING,
        getFormattedSummary(INFO_SUMMARY_DOWNLOADING.get()));
    hmSummary.put(InstallProgressStep.EXTRACTING,
        getFormattedSummary(INFO_SUMMARY_EXTRACTING.get()));
    hmSummary.put(InstallProgressStep.CONFIGURING_SERVER,
        getFormattedSummary(INFO_SUMMARY_CONFIGURING.get()));
    hmSummary.put(InstallProgressStep.CREATING_BASE_ENTRY,
        getFormattedSummary(INFO_SUMMARY_CREATING_BASE_ENTRY.get()));
    hmSummary.put(InstallProgressStep.IMPORTING_LDIF,
        getFormattedSummary(INFO_SUMMARY_IMPORTING_LDIF.get()));
    hmSummary.put(
        InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        getFormattedSummary(
            INFO_SUMMARY_IMPORTING_AUTOMATICALLY_GENERATED.get()));
    hmSummary.put(InstallProgressStep.CONFIGURING_REPLICATION,
        getFormattedSummary(INFO_SUMMARY_CONFIGURING_REPLICATION.get()));
    hmSummary.put(InstallProgressStep.STARTING_SERVER,
        getFormattedSummary(INFO_SUMMARY_STARTING.get()));
    hmSummary.put(InstallProgressStep.STOPPING_SERVER,
        getFormattedSummary(INFO_SUMMARY_STOPPING.get()));
    hmSummary.put(InstallProgressStep.CONFIGURING_ADS,
        getFormattedSummary(INFO_SUMMARY_CONFIGURING_ADS.get()));
    hmSummary.put(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES,
        getFormattedSummary(INFO_SUMMARY_INITIALIZE_REPLICATED_SUFFIXES.get()));
    hmSummary.put(InstallProgressStep.ENABLING_WINDOWS_SERVICE,
        getFormattedSummary(INFO_SUMMARY_ENABLING_WINDOWS_SERVICE.get()));
    hmSummary.put(InstallProgressStep.WAITING_TO_CANCEL,
        getFormattedSummary(INFO_SUMMARY_WAITING_TO_CANCEL.get()));
    hmSummary.put(InstallProgressStep.CANCELING,
        getFormattedSummary(INFO_SUMMARY_CANCELING.get()));

    Installation installation = getInstallation();
    String cmd = Utils.addWordBreaks(
        getPath(installation.getControlPanelCommandFile()), 60, 5);
    if (!isCli)
    {
      cmd = UIFactory.applyFontToHtml(cmd,
                    UIFactory.INSTRUCTIONS_MONOSPACE_FONT);
    }
    String formattedPath = Utils.addWordBreaks(
        formatter.getFormattedText(
            Message.raw(getPath(new File(getInstancePath())))).toString(),
            60, 5);
    Message successMessage = Utils.getCustomizedObject(
        "INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY",
        INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY.get(
            DynamicConstants.PRODUCT_NAME,
            DynamicConstants.PRODUCT_NAME,
            formattedPath,
            INFO_GENERAL_SERVER_STOPPED.get(),
            DynamicConstants.DOC_QUICK_REFERENCE_GUIDE,
            DynamicConstants.PRODUCT_NAME,
            cmd), Message.class);
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY,
            getFormattedSuccess(successMessage));
    hmSummary.put(InstallProgressStep.FINISHED_CANCELED,
            getFormattedSuccess(INFO_SUMMARY_INSTALL_FINISHED_CANCELED.get()));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR,
            getFormattedError(INFO_SUMMARY_INSTALL_FINISHED_WITH_ERROR.get(
                    INFO_GENERAL_SERVER_STOPPED.get(),
                    cmd)));
  }

  /**
   * Updates the messages in the summary with the state of the server.
   * @param hmSummary the Map containing the messages.
   * @param isCli a boolean to indicate if the install is using CLI or GUI
   */
  protected void updateSummaryWithServerState(
      Map<InstallProgressStep, Message> hmSummary, Boolean isCli)
  {
   Installation installation = getInstallation();
   String cmd = getPath(installation.getControlPanelCommandFile());
   if (! isCli)
   {
     cmd = Utils.addWordBreaks(
       UIFactory.applyFontToHtml(cmd, UIFactory.INSTRUCTIONS_MONOSPACE_FONT),
       60, 5);
   }
   Message status;
   if (installation.getStatus().isServerRunning())
   {
     status = INFO_GENERAL_SERVER_STARTED.get();
   }
   else
   {
     status = INFO_GENERAL_SERVER_STOPPED.get();
   }
   String formattedPath = Utils.addWordBreaks(
   formatter.getFormattedText(
       Message.raw(getPath(new File(getInstancePath())))).toString(),
       60, 5);
   Message successMessage = Utils.getCustomizedObject(
       "INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY",
      INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY.get(
            DynamicConstants.PRODUCT_NAME,
            DynamicConstants.PRODUCT_NAME,
            formattedPath,
            status,
            DynamicConstants.DOC_QUICK_REFERENCE_GUIDE,
            DynamicConstants.PRODUCT_NAME,
            cmd), Message.class);
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY,
            getFormattedSuccess(successMessage));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR,
            getFormattedError(INFO_SUMMARY_INSTALL_FINISHED_WITH_ERROR.get(
                    status,
                    cmd)));
  }
  /**
   * Checks the value of <code>canceled</code> field and throws an
   * ApplicationException if true.  This indicates that the user has
   * canceled this operation and the process of aborting should begin
   * as soon as possible.
   *
   * @throws ApplicationException thrown if <code>canceled</code>
   */
  @Override
  public void checkAbort() throws ApplicationException {
    if (canceled) {
      setCurrentProgressStep(InstallProgressStep.CANCELING);
      notifyListeners(null);
      throw new ApplicationException(
          ReturnCode.CANCELED,
          INFO_INSTALL_CANCELED.get(), null);
    }
  }

  /**
   * Writes the host name to a file that will be used by the server to generate
   * a self-signed certificate.
   */
  private void writeHostName()
  {
    BufferedWriter writer = null;
    try
    {
      writer = new BufferedWriter(new FileWriter(getHostNameFile(), false));
      writer.append(getUserData().getHostName());
    }
    catch (IOException ioe)
    {
      LOG.log(Level.WARNING, "Error writing host name file: "+ioe, ioe);
    }
    finally
    {
      StaticUtils.close(writer);
    }
  }

  /**
   * Returns the file path where the host name is to be written.
   * @return the file path where the host name is to be written.
   */
  private String getHostNameFile()
  {
    return Utils.getPath(
        getInstallation().getRootDirectory().getAbsolutePath(),
        SetupUtils.HOST_NAME_FILE);
  }

  /**
   * Writes the java home that we are using for the setup in a file.
   * This way we can use this java home even if the user has not set
   * OPENDJ_JAVA_HOME when running the different scripts.
   *
   */
  private void writeOpenDSJavaHome()
  {
    try
    {
      // This isn't likely to happen, and it's not a serious problem even if
      // it does.
      InstallerHelper helper = new InstallerHelper();
      helper.writeSetOpenDSJavaHome(getUserData(), getInstallationPath());
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Error writing OpenDJ Java Home file: "+e, e);
    }
  }

  /**
   * These methods validate the data provided by the user in the panels and
   * update the userData object according to that content.
   *
   * @param cStep
   *          the current step of the wizard
   * @param qs QuickStart controller
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  @Override
  public void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException
  {
    if (cStep == SERVER_SETTINGS)
    {
      updateUserDataForServerSettingsPanel(qs);
    }
    else if (cStep == REPLICATION_OPTIONS)
    {
      updateUserDataForReplicationOptionsPanel(qs);
    }
    else if (cStep == CREATE_GLOBAL_ADMINISTRATOR)
    {
      updateUserDataForCreateAdministratorPanel(qs);
    }
    else if (cStep == SUFFIXES_OPTIONS)
    {
      updateUserDataForSuffixesOptionsPanel(qs);
    }
    else if (cStep == REMOTE_REPLICATION_PORTS)
    {
      updateUserDataForRemoteReplicationPorts(qs);
    }
    else if (cStep == NEW_SUFFIX_OPTIONS)
    {
      updateUserDataForNewSuffixOptionsPanel(qs);
    }
    else if (cStep == RUNTIME_OPTIONS)
    {
      updateUserDataForRuntimeOptionsPanel(qs);
    }
    else if (cStep ==  REVIEW)
    {
      updateUserDataForReviewPanel(qs);
    }
  }

  /**
   * Returns the default backend name (the one that will be created).
   * @return the default backend name (the one that will be created).
   */
  private String getBackendName()
  {
    return "userRoot";
  }

  /**
   * Sets the current progress step of the installation process.
   * @param currentProgressStep the current progress step of the installation
   * process.
   */
  protected void setCurrentProgressStep(InstallProgressStep currentProgressStep)
  {
    if (currentProgressStep != null) {
      this.completedProgress.add(currentProgressStep);
    }
    this.currentProgressStep = currentProgressStep;
  }

  /**
   * This methods updates the data on the server based on the contents of the
   * UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  protected void createData() throws ApplicationException
  {
    if (createNotReplicatedSuffix())
    {
      switch (getUserData().getNewSuffixOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        currentProgressStep = InstallProgressStep.CREATING_BASE_ENTRY;
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        createBaseEntry();
        break;
      case IMPORT_FROM_LDIF_FILE:
        currentProgressStep = InstallProgressStep.IMPORTING_LDIF;
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        importLDIF();
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        currentProgressStep =
          InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED;
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        importAutomaticallyGenerated();
        break;
      }
    }
  }

  /**
   * This method initialize the contents of the synchronized servers with the
   * contents of the first server we find.
   * @throws ApplicationException if something goes wrong.
   */
  protected void initializeSuffixes() throws ApplicationException
  {
    InitialLdapContext ctx = null;
    try
    {
      ctx = createLocalContext();
    }
    catch (Throwable t)
    {
      Message failedMsg =
              getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), t);
      StaticUtils.close(ctx);
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR, failedMsg, t);
    }

    Set<SuffixDescriptor> suffixes =
      getUserData().getSuffixesToReplicateOptions().getSuffixes();

    /* Initialize local ADS and schema contents using any replica. */
    {
      ServerDescriptor server
       = suffixes.iterator().next().getReplicas().iterator().next().getServer();
      InitialLdapContext rCtx = null;
      try
      {
        rCtx = getRemoteConnection(server, getTrustManager(),
            getPreferredConnections());
        TopologyCacheFilter filter = new TopologyCacheFilter();
        filter.setSearchMonitoringInformation(false);
        filter.addBaseDNToSearch(ADSContext.getAdministrationSuffixDN());
        filter.addBaseDNToSearch(Constants.SCHEMA_DN);
        ServerDescriptor s = ServerDescriptor.createStandalone(rCtx, filter);
        for (ReplicaDescriptor replica : s.getReplicas())
        {
          String dn = replica.getSuffix().getDN();
          if (areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()))
          {
            suffixes.add(replica.getSuffix());
          }
          else if (areDnsEqual(dn, Constants.SCHEMA_DN))
          {
            suffixes.add(replica.getSuffix());
          }
        }
      }
      catch (NamingException ne)
      {
        Message msg;
        if (Utils.isCertificateException(ne))
        {
          msg = INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
              getHostPort(server), ne.toString(true));
        }
        else
        {
           msg = INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
               getHostPort(server), ne.toString(true));
        }
        throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, msg,
            ne);
      }
      finally
      {
        StaticUtils.close(rCtx);
      }
    }

    for (SuffixDescriptor suffix : suffixes)
    {
      String dn = suffix.getDN();

      ReplicaDescriptor replica = suffix.getReplicas().iterator().next();
      ServerDescriptor server = replica.getServer();
      String hostPort = getHostPort(server);

      boolean isADS = areDnsEqual(dn, ADSContext.getAdministrationSuffixDN());
      boolean isSchema = areDnsEqual(dn, Constants.SCHEMA_DN);
      if(isADS)
      {
        if (isVerbose())
        {
          notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_INITIALIZING_ADS.get()));
        }
      }
      else if (isSchema)
      {
        if (isVerbose())
        {
          notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_INITIALIZING_SCHEMA.get()));
        }
      }
      else
      {
        notifyListeners(getFormattedProgress(
            INFO_PROGRESS_INITIALIZING_SUFFIX.get(dn, hostPort)));
        notifyListeners(getLineBreak());
      }
      try
      {
        int replicationId = replica.getReplicationId();
        if (replicationId == -1)
        {
          /**
           * This occurs if the remote server had not replication configured.
           */
          InitialLdapContext rCtx = null;
          try
          {
            rCtx = getRemoteConnection(server, getTrustManager(),
                getPreferredConnections());
            TopologyCacheFilter filter = new TopologyCacheFilter();
            filter.setSearchMonitoringInformation(false);
            filter.addBaseDNToSearch(dn);
            ServerDescriptor s = ServerDescriptor.createStandalone(rCtx,
                filter);
            for (ReplicaDescriptor r : s.getReplicas())
            {
              if (areDnsEqual(r.getSuffix().getDN(), dn))
              {
                replicationId = r.getReplicationId();
              }
            }
          }
          catch (NamingException ne)
          {
            Message msg;
            if (Utils.isCertificateException(ne))
            {
              msg = INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
                  getHostPort(server), ne.toString(true));
            }
            else
            {
               msg = INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
                   getHostPort(server), ne.toString(true));
            }
            throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, msg,
                ne);
          }
          finally
          {
            StaticUtils.close(rCtx);
          }
        }
        if (replicationId == -1)
        {
          throw new ApplicationException(
              ReturnCode.APPLICATION_ERROR,
              ERR_COULD_NOT_FIND_REPLICATIONID.get(dn), null);
        }
        StaticUtils.sleep(3000);
        int nTries = 5;
        boolean initDone = false;
        while (!initDone)
        {
          try
          {
            LOG.log(Level.INFO, "Calling initializeSuffix with base DN: "+dn);
            LOG.log(Level.INFO, "Try number: "+(6 - nTries));
            LOG.log(Level.INFO, "replicationId of source replica: "+
                replicationId);
            initializeSuffix(ctx, replicationId, dn, !isADS && !isSchema,
                hostPort);
            initDone = true;
          }
          catch (PeerNotFoundException pnfe)
          {
            LOG.log(Level.INFO, "Peer could not be found");
            if (nTries == 1)
            {
              throw new ApplicationException(
                  ReturnCode.APPLICATION_ERROR,
                  pnfe.getMessageObject(), null);
            }
            StaticUtils.sleep((5 - nTries) * 3000);
          }
          nTries--;
        }
      }
      catch (ApplicationException ae)
      {
        StaticUtils.close(ctx);
        throw ae;
      }
      if ((isADS || isSchema) && isVerbose())
      {
        notifyListeners(getFormattedDone());
        notifyListeners(getLineBreak());
      }
      checkAbort();
    }
  }

  /**
   * This method updates the ADS contents (and creates the according suffixes).
   * If the user specified an existing topology, the new instance is
   * registered with that ADS (the ADS might need to be created), and the
   * local ADS will be populated when the local server is added to the remote
   * server's ADS replication domain in a subsequent step. Otherwise, an ADS
   * is created on the new instance and the server is registered with the new
   * ADS. NOTE: this method assumes that the local server and any remote server
   * are running.
   * @throws ApplicationException if something goes wrong.
   */
  protected void updateADS() throws ApplicationException
  {
    DataReplicationOptions repl = getUserData().getReplicationOptions();
    boolean isRemoteServer =
            repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
    AuthenticationData auth = (isRemoteServer) ? repl.getAuthenticationData()
                                             : null;
    InitialLdapContext remoteCtx = null; // Bound to remote ADS host (if any).
    InitialLdapContext localCtx = null; // Bound to local server.
    ADSContext adsContext = null; // Bound to ADS host (via one of above).

    /* Outer try-catch-finally to convert occurrences of NamingException and
       ADSContextException to ApplicationException and clean up JNDI contexts.*/
    try
    {
      if (isRemoteServer)
      {
        /* In case the user specified an existing topology... */
        String ldapUrl = getLdapUrl(auth);
        String dn = auth.getDn();
        String pwd = auth.getPwd();
        if (auth.useSecureConnection())
        {
          ApplicationTrustManager trustManager = getTrustManager();
          trustManager.setHost(auth.getHostName());
          remoteCtx = createLdapsContext(ldapUrl, dn, pwd,
              getConnectTimeout(), null, trustManager);
        }
        else
        {
          remoteCtx = createLdapContext(ldapUrl, dn, pwd,
              getConnectTimeout(), null);
        }
        adsContext = new ADSContext(remoteCtx); // adsContext owns remoteCtx

        /* Check the remote server for ADS. If it does not exist, create the
           initial ADS there and register the server with itself. */
        if (! adsContext.hasAdminData())
        {
          if (isVerbose())
          {
            notifyListeners(getFormattedWithPoints(
               INFO_PROGRESS_CREATING_ADS_ON_REMOTE.get(getHostDisplay(auth))));
          }

          adsContext.createAdminData(null);
          TopologyCacheFilter filter = new TopologyCacheFilter();
          filter.setSearchMonitoringInformation(false);
          filter.setSearchBaseDNInformation(false);
          ServerDescriptor server
                  = ServerDescriptor.createStandalone(remoteCtx, filter);
          server.updateAdsPropertiesWithServerProperties();
          adsContext.registerServer(server.getAdsProperties());
          createdRemoteAds = true;
          if (isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
          checkAbort();
        }
      }

      /* Act on local server depending on if using remote or local ADS */
      if (isVerbose())
      {
        notifyListeners(
            getFormattedWithPoints(INFO_PROGRESS_CREATING_ADS.get()));
      }
      localCtx = createLocalContext();
//      if (isRemoteServer)
//      {
//        /* Create an empty ADS suffix on the local server. */
//        ADSContext localAdsContext = new ADSContext(localCtx);
//        localAdsContext.createAdministrationSuffix(null);
//      }
      if (!isRemoteServer)
      {
        /* Configure local server to have an ADS */
        adsContext = new ADSContext(localCtx); // adsContext owns localCtx
        adsContext.createAdminData(null);
      }
      assert null != adsContext ; // Bound either to local or remote ADS.

      /* Register new server in ADS. */
      TopologyCacheFilter filter = new TopologyCacheFilter();
      filter.setSearchMonitoringInformation(false);
      filter.setSearchBaseDNInformation(false);
      ServerDescriptor server = ServerDescriptor.createStandalone(localCtx,
          filter);
      server.updateAdsPropertiesWithServerProperties();
      if (0 == adsContext.registerOrUpdateServer(server.getAdsProperties())) {
        if (isRemoteServer) registeredNewServerOnRemote = true;
      } else {
        LOG.log(Level.WARNING, "Server was already registered. Updating " +
                "server registration.");
      }
      if (isRemoteServer)
      {
        ServerDescriptor.seedAdsTrustStore(localCtx,
                                           adsContext.getTrustedCertificates());
      }
      if (isVerbose())
      {
        notifyListeners(getFormattedDoneWithLineBreak());
      }
      checkAbort();

      /* Add global administrator if the user specified one. */
      if (getUserData().mustCreateAdministrator())
      {
        try
        {
          if (isVerbose())
          {
            notifyListeners(getFormattedWithPoints(
                  INFO_PROGRESS_CREATING_ADMINISTRATOR.get()));
          }
          adsContext.createAdministrator(getAdministratorProperties(
                  getUserData()));
          if (isRemoteServer && !createdRemoteAds) createdAdministrator = true;
          if (isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
          checkAbort();
        }
        catch (ADSContextException ade)
        {
          if (ade.getError() ==
                  ADSContextException.ErrorType.ALREADY_REGISTERED)
          {
            notifyListeners(getFormattedWarning(
                INFO_ADMINISTRATOR_ALREADY_REGISTERED.get()));
            adsContext.unregisterServer(server.getAdsProperties());
            adsContext.registerServer(server.getAdsProperties());
          }
          else
          {
            throw ade;
          }
        }
      }
    }
    catch (NamingException ne)
    {
      Message msg;
      if (isRemoteServer)
      {
        msg = Utils.getMessageForException(ne, getHostDisplay(auth));
      }
      else
      {
        msg = Utils.getMessageForException(ne);
      }
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR,
          msg,
          ne);
    }
    catch (ADSContextException ace)
    {
      throw new ApplicationException(
              ReturnCode.CONFIGURATION_ERROR,
              ((isRemoteServer)
                      ? INFO_REMOTE_ADS_EXCEPTION.get(
                      getHostDisplay(auth), ace.getMessageObject())
                      : INFO_ADS_EXCEPTION.get(ace.toString())), ace);
    }
    finally
    {
      StaticUtils.close(remoteCtx, localCtx);
    }
  }

  /**
   * Tells whether we must create a suffix that we are not going to replicate
   * with other servers or not.
   * @return <CODE>true</CODE> if we must create a new suffix and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean createNotReplicatedSuffix()
  {
    boolean createSuffix;

    DataReplicationOptions repl =
      getUserData().getReplicationOptions();

    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();

    createSuffix =
      (repl.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY) ||
      (repl.getType() == DataReplicationOptions.Type.STANDALONE) ||
      (suf.getType() == SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY);

    return createSuffix;
  }

  /**
   * Returns <CODE>true</CODE> if we must configure replication and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must configure replication and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustConfigureReplication()
  {
    return getUserData().getReplicationOptions().getType() !=
      DataReplicationOptions.Type.STANDALONE;
  }

  /**
   * Returns <CODE>true</CODE> if we must create the ADS and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must create the ADS and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustCreateAds()
  {
    return getUserData().getReplicationOptions().getType() !=
      DataReplicationOptions.Type.STANDALONE;
  }

  /**
   * Returns <CODE>true</CODE> if we must start the server and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must start the server and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustStart()
  {
    return getUserData().getStartServer() || mustCreateAds();
  }

  /**
   * Returns <CODE>true</CODE> if the start server must be launched in verbose
   * mode and <CODE>false</CODE> otherwise.  The verbose flag is  not enough
   * because in the case where many entries have been imported, the startup
   * phase can take long.
   * @return <CODE>true</CODE> if the start server must be launched in verbose
   * mode and <CODE>false</CODE> otherwise.
   */
  protected boolean isStartVerbose()
  {
    if (isVerbose())
    {
      return true;
    }
    boolean manyEntriesToImport = false;
    NewSuffixOptions.Type type = getUserData().getNewSuffixOptions().getType();
    if (type == NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE)
    {
      long mbTotalSize = 0;
      LinkedList<String> ldifPaths =
        getUserData().getNewSuffixOptions().getLDIFPaths();
      for (String ldifPath : ldifPaths)
      {
        File f = new File(ldifPath);
        mbTotalSize += f.length();
      }
      // Assume entries of 1kb
      if (mbTotalSize > NENTRIES_THRESOLD_FOR_VERBOSE_START * 1024)
      {
        manyEntriesToImport = true;
      }
    }
    else if (type == NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA)
    {
      int nEntries = getUserData().getNewSuffixOptions().getNumberEntries();
      if (nEntries > NENTRIES_THRESOLD_FOR_VERBOSE_START)
      {
        manyEntriesToImport = true;
      }
    }
    return manyEntriesToImport;
  }

  /**
   * Returns <CODE>true</CODE> if we must stop the server and
   * <CODE>false</CODE> otherwise.
   * The server might be stopped if the user asked not to start it at the
   * end of the installation and it was started temporarily to update its
   * configuration.
   * @return <CODE>true</CODE> if we must stop the server and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustStop()
  {
    return !getUserData().getStartServer() && mustCreateAds();
  }

  /**
   * Returns <CODE>true</CODE> if we must initialize suffixes and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must initialize suffixes and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustInitializeSuffixes()
  {
    return getUserData().getReplicationOptions().getType() ==
      DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
  }

  /**
   * Returns the list of preferred URLs to connect to remote servers.  In fact
   * it returns only the URL to the remote server specified by the user in
   * the replication options panel.  The method returns a list for convenience
   * with other interfaces.
   * NOTE: this method assumes that the UserData object has already been updated
   * with the host and port of the remote server.
   * @return the list of preferred URLs to connect to remote servers.
   */
  private LinkedHashSet<PreferredConnection> getPreferredConnections()
  {
    LinkedHashSet<PreferredConnection> cnx =
      new LinkedHashSet<PreferredConnection>();
    DataReplicationOptions repl = getUserData().getReplicationOptions();
    if (repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      AuthenticationData auth = repl.getAuthenticationData();
      if (auth != null)
      {
        PreferredConnection.Type type;
        if (auth.useSecureConnection())
        {
          type = PreferredConnection.Type.LDAPS;
        }
        else
        {
          type = PreferredConnection.Type.LDAP;
        }
        cnx.add(new PreferredConnection(getLdapUrl(auth), type));
      }
    }
    return cnx;
  }

  private String getLdapUrl(AuthenticationData auth)
  {
    String ldapUrl;
    if (auth.useSecureConnection())
    {
      ldapUrl = "ldaps://"+auth.getHostName()+":"+auth.getPort();
    }
    else
    {
      ldapUrl = "ldap://"+auth.getHostName()+":"+auth.getPort();
    }
    return ldapUrl;
  }

  private String getHostDisplay(AuthenticationData auth)
  {
    return auth.getHostName()+":"+auth.getPort();
  }

  private Map<ADSContext.ServerProperty, Object>
  getNewServerAdsProperties(UserData userData)
  {
    Map<ADSContext.ServerProperty, Object> serverProperties =
      new HashMap<ADSContext.ServerProperty, Object>();
    serverProperties.put(ADSContext.ServerProperty.HOST_NAME,
          userData.getHostName());
    serverProperties.put(ADSContext.ServerProperty.LDAP_PORT,
        String.valueOf(userData.getServerPort()));
    serverProperties.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");

    // TODO: even if the user does not configure SSL maybe we should choose
    // a secure port that is not being used and that we can actually use.
    SecurityOptions sec = userData.getSecurityOptions();
    if (sec.getEnableSSL())
    {
      serverProperties.put(ADSContext.ServerProperty.LDAPS_PORT,
          String.valueOf(sec.getSslPort()));
      serverProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "true");
    }
    else
    {
      serverProperties.put(ADSContext.ServerProperty.LDAPS_PORT, "636");
      serverProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "false");
    }

    if (sec.getEnableStartTLS())
    {
      serverProperties.put(ADSContext.ServerProperty.STARTTLS_ENABLED, "true");
    }
    else
    {
      serverProperties.put(ADSContext.ServerProperty.STARTTLS_ENABLED, "false");
    }

    serverProperties.put(ADSContext.ServerProperty.JMX_PORT, "1689");
    serverProperties.put(ADSContext.ServerProperty.JMX_ENABLED, "false");

    String path;
    if (isWebStart())
    {
      path = userData.getServerLocation();
    }
    else
    {
      path = getInstallPathFromClasspath();
    }
    serverProperties.put(ADSContext.ServerProperty.INSTANCE_PATH, path);

    String serverID = serverProperties.get(ADSContext.ServerProperty.HOST_NAME)+
    ":"+userData.getServerPort();

    /* TODO: do we want to ask this specifically to the user? */
    serverProperties.put(ADSContext.ServerProperty.ID, serverID);

    serverProperties.put(ADSContext.ServerProperty.HOST_OS,
        getOSString());
    return serverProperties;
  }

  private Map<ADSContext.AdministratorProperty, Object>
  getAdministratorProperties(UserData userData)
  {
    Map<ADSContext.AdministratorProperty, Object> adminProperties =
      new HashMap<ADSContext.AdministratorProperty, Object>();
    adminProperties.put(ADSContext.AdministratorProperty.UID,
        userData.getGlobalAdministratorUID());
    adminProperties.put(ADSContext.AdministratorProperty.PASSWORD,
        userData.getGlobalAdministratorPassword());
    adminProperties.put(ADSContext.AdministratorProperty.DESCRIPTION,
        INFO_GLOBAL_ADMINISTRATOR_DESCRIPTION.get().toString());
    return adminProperties;
  }

  /**
   * Validate the data provided by the user in the server settings panel and
   * update the userData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForServerSettingsPanel(QuickSetup qs)
      throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();
    Message confirmationMsg = null;

    if (isWebStart())
    {
      // Check the server location
      String serverLocation = qs.getFieldStringValue(FieldName.SERVER_LOCATION);

      if ((serverLocation == null) || ("".equals(serverLocation.trim())))
      {
        errorMsgs.add(INFO_EMPTY_SERVER_LOCATION.get());
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (!parentDirectoryExists(serverLocation))
      {
        String existingParentDirectory = null;
        File f = new File(serverLocation);
        while ((existingParentDirectory == null) && (f != null))
        {
          f = f.getParentFile();
          if ((f != null) && f.exists())
          {
            if (f.isDirectory())
            {
              existingParentDirectory = f.getAbsolutePath();
            }
            else
            {
              // The parent path is a file!
              f = null;
            }
          }
        }
        if (existingParentDirectory == null)
        {
          errorMsgs.add(INFO_PARENT_DIRECTORY_COULD_NOT_BE_FOUND.get(
                  serverLocation));
          qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
        }
        else
        {
          if (!canWrite(existingParentDirectory))
          {
            errorMsgs.add(INFO_DIRECTORY_NOT_WRITABLE.get(
                    existingParentDirectory));
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
          }
          else if (!hasEnoughSpace(existingParentDirectory,
              getRequiredInstallSpace()))
          {
            long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
            errorMsgs.add(INFO_NOT_ENOUGH_DISK_SPACE.get(
                    existingParentDirectory, String.valueOf(requiredInMb)));
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
          }
          else
          {
            confirmationMsg =
              INFO_PARENT_DIRECTORY_DOES_NOT_EXIST_CONFIRMATION.get(
                      serverLocation);
            getUserData().setServerLocation(serverLocation);
          }
        }
      } else if (fileExists(serverLocation))
      {
        errorMsgs.add(INFO_FILE_EXISTS.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else if (directoryExistsAndIsNotEmpty(serverLocation))
      {
        errorMsgs.add(INFO_DIRECTORY_EXISTS_NOT_EMPTY.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else if (!canWrite(serverLocation))
      {
        errorMsgs.add(INFO_DIRECTORY_NOT_WRITABLE.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);

      } else if (!hasEnoughSpace(serverLocation,
          getRequiredInstallSpace()))
      {
        long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
        errorMsgs.add(INFO_NOT_ENOUGH_DISK_SPACE.get(
                serverLocation, String.valueOf(requiredInMb)));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);

      } else if (isWindows() && (serverLocation.contains("%")))
      {
        errorMsgs.add(INFO_INVALID_CHAR_IN_PATH.get("%"));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else
      {
        getUserData().setServerLocation(serverLocation);
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, false);
      }
    }

    // Check the host is not empty.
    // TODO: check that the host name is valid...
    String hostName = qs.getFieldStringValue(FieldName.HOST_NAME);
    if ((hostName == null) || hostName.trim().length() == 0)
    {
      errorMsgs.add(INFO_EMPTY_HOST_NAME.get());
      qs.displayFieldInvalid(FieldName.HOST_NAME, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.HOST_NAME, false);
      getUserData().setHostName(hostName);
    }

    // Check the port
    String sPort = qs.getFieldStringValue(FieldName.SERVER_PORT);
    int port = -1;
    try
    {
      port = Integer.parseInt(sPort);
      if ((port < MIN_PORT_VALUE) || (port > MAX_PORT_VALUE))
      {
        errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(
                String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      } else if (!canUseAsPort(port))
      {
        if (isPriviledgedPort(port))
        {
          errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
            String.valueOf(port)));
        } else
        {
          errorMsgs.add(INFO_CANNOT_BIND_PORT.get(String.valueOf(port)));
        }
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);

      } else
      {
        getUserData().setServerPort(port);
        qs.displayFieldInvalid(FieldName.SERVER_PORT, false);
      }

    } catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(
              String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE)));
      qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
    }

    //  Check the admin connector port
    sPort = qs.getFieldStringValue(FieldName.ADMIN_CONNECTOR_PORT);
    int adminConnectorPort = -1;
    try
    {
      adminConnectorPort = Integer.parseInt(sPort);
      if ((adminConnectorPort < MIN_PORT_VALUE) ||
          (adminConnectorPort > MAX_PORT_VALUE))
      {
        errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(
                String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
      } else if (!canUseAsPort(adminConnectorPort))
      {
        if (isPriviledgedPort(adminConnectorPort))
        {
          errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
            String.valueOf(adminConnectorPort)));
        } else
        {
          errorMsgs.add(INFO_CANNOT_BIND_PORT.get(
              String.valueOf(adminConnectorPort)));
        }
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);

      }
      else if (adminConnectorPort == port)
      {
        errorMsgs.add(INFO_ADMIN_CONNECTOR_VALUE_SEVERAL_TIMES.get());
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
      }
      else
      {
        getUserData().setAdminConnectorPort(adminConnectorPort);
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, false);
      }

    } catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(
              String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE)));
      qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
    }

    // Check the secure port
    SecurityOptions sec =
      (SecurityOptions)qs.getFieldValue(FieldName.SECURITY_OPTIONS);
    int securePort = sec.getSslPort();
    if (sec.getEnableSSL())
    {
      if ((securePort < MIN_PORT_VALUE) || (securePort > MAX_PORT_VALUE))
      {
        errorMsgs.add(INFO_INVALID_SECURE_PORT_VALUE_RANGE.get(
                String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
      } else if (!canUseAsPort(securePort))
      {
        if (isPriviledgedPort(securePort))
        {
          errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
            String.valueOf(securePort)));
        } else
        {
          errorMsgs.add(INFO_CANNOT_BIND_PORT.get(String.valueOf(securePort)));
        }
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);

      }
      else if (port == securePort)
      {
        errorMsgs.add(INFO_EQUAL_PORTS.get());
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      }
      else if (adminConnectorPort == securePort)
      {
        errorMsgs.add(INFO_ADMIN_CONNECTOR_VALUE_SEVERAL_TIMES.get());
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
      }
      else
      {
        getUserData().setSecurityOptions(sec);
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, false);
      }
    }
    else
    {
      getUserData().setSecurityOptions(sec);
      qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, false);
    }


    // Check the Directory Manager DN
    String dmDn = qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_DN);

    if ((dmDn == null) || (dmDn.trim().length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_DIRECTORY_MANAGER_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else if (!isDn(dmDn))
    {
      errorMsgs.add(INFO_NOT_A_DIRECTORY_MANAGER_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else if (isConfigurationDn(dmDn))
    {
      errorMsgs.add(INFO_DIRECTORY_MANAGER_DN_IS_CONFIG_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else
    {
      getUserData().setDirectoryManagerDn(dmDn);
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, false);
    }

    // Check the provided passwords
    String pwd1 = qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_PWD);
    String pwd2 =
            qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM);
    if (pwd1 == null)
    {
      pwd1 = "";
    }

    boolean pwdValid = true;
    if (!pwd1.equals(pwd2))
    {
      errorMsgs.add(INFO_NOT_EQUAL_PWD.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM, true);
      pwdValid = false;

    }
    if (pwd1.length() < MIN_DIRECTORY_MANAGER_PWD)
    {
      errorMsgs.add(INFO_PWD_TOO_SHORT.get(
              String.valueOf(MIN_DIRECTORY_MANAGER_PWD)));
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD, true);
      if ((pwd2 == null) || (pwd2.length() < MIN_DIRECTORY_MANAGER_PWD))
      {
        qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM, true);
      }
      pwdValid = false;
    }

    if (pwdValid)
    {
      getUserData().setDirectoryManagerPwd(pwd1);
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD, false);
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM, false);
    }

    // For the moment do not enable JMX
    int defaultJMXPort =
      UserData.getDefaultJMXPort(new int[] {port, securePort});
    if (defaultJMXPort != -1)
    {
      //getUserData().setServerJMXPort(defaultJMXPort);
      getUserData().setServerJMXPort(-1);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.SERVER_SETTINGS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
    if (confirmationMsg != null)
    {
      throw new UserDataConfirmationException(Step.SERVER_SETTINGS,
          confirmationMsg);
    }
  }

  /**
   * Validate the data provided by the user in the data options panel and update
   * the userData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForReplicationOptionsPanel(QuickSetup qs)
      throws UserDataException {
    boolean hasGlobalAdministrators = false;
    Integer replicationPort = -1;
    boolean secureReplication = false;
    Integer port = null;
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    DataReplicationOptions.Type type = (DataReplicationOptions.Type)
      qs.getFieldValue(FieldName.REPLICATION_OPTIONS);
    String host = qs.getFieldStringValue(FieldName.REMOTE_SERVER_HOST);
    String dn = qs.getFieldStringValue(FieldName.REMOTE_SERVER_DN);
    String pwd = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PWD);

    if (type != DataReplicationOptions.Type.STANDALONE)
    {
      // Check replication port
      replicationPort = checkReplicationPort(qs, errorMsgs);
      secureReplication =
        (Boolean)qs.getFieldValue(FieldName.REPLICATION_SECURE);
    }

    UserDataConfirmationException confirmEx = null;
    switch (type)
    {
    case IN_EXISTING_TOPOLOGY:
    {
      String sPort = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PORT);
      checkRemoteHostPortDnAndPwd(host, sPort, dn, pwd, qs, errorMsgs);

      if (errorMsgs.isEmpty())
      {
        port = Integer.parseInt(sPort);
        // Try to connect
        boolean[] globalAdmin = {hasGlobalAdministrators};
        String[] effectiveDn = {dn};
        try
        {
          updateUserDataWithADS(host, port, dn, pwd, qs, errorMsgs,
              globalAdmin, effectiveDn);
        }
        catch (UserDataConfirmationException e)
        {
          confirmEx = e;
        }
        hasGlobalAdministrators = globalAdmin[0];
        dn = effectiveDn[0];
      }
      break;
    }
    case STANDALONE:
    {
      getUserData().setSuffixesToReplicateOptions(
          new SuffixesToReplicateOptions(
              SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE,
              new HashSet<SuffixDescriptor>(),
              new HashSet<SuffixDescriptor>()));
      break;
    }
    case FIRST_IN_TOPOLOGY:
    {
      getUserData().setSuffixesToReplicateOptions(
          new SuffixesToReplicateOptions(
              SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY,
              new HashSet<SuffixDescriptor>(),
              new HashSet<SuffixDescriptor>()));
      break;
    }
    default:
      throw new IllegalStateException("Do not know what to do with type: "+
          type);
    }

    if (errorMsgs.isEmpty())
    {
      AuthenticationData auth = new AuthenticationData();
      auth.setHostName(host);
      if (port != null)
      {
        auth.setPort(port);
      }
      auth.setDn(dn);
      auth.setPwd(pwd);
      auth.setUseSecureConnection(true);

      DataReplicationOptions repl;
      switch (type)
      {
      case IN_EXISTING_TOPOLOGY:
      {
        repl = DataReplicationOptions.createInExistingTopology(auth,
            replicationPort, secureReplication);
        break;
      }
      case STANDALONE:
      {
        repl = DataReplicationOptions.createStandalone();
        break;
      }
      case FIRST_IN_TOPOLOGY:
      {
        repl = DataReplicationOptions.createFirstInTopology(replicationPort,
            secureReplication);
        break;
      }
      default:
        throw new IllegalStateException("Do not know what to do with type: "+
            type);
      }
      getUserData().setReplicationOptions(repl);

      getUserData().createAdministrator(!hasGlobalAdministrators &&
      type == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY);
    }
    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.REPLICATION_OPTIONS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
    if (confirmEx != null)
    {
      throw confirmEx;
    }
  }

  private int checkReplicationPort(QuickSetup qs, ArrayList<Message> errorMsgs)
  {
    int replicationPort = -1;
    String sPort = qs.getFieldStringValue(FieldName.REPLICATION_PORT);
    try
    {
      replicationPort = Integer.parseInt(sPort);
      if ((replicationPort < MIN_PORT_VALUE) ||
          (replicationPort > MAX_PORT_VALUE))
      {
        errorMsgs.add(INFO_INVALID_REPLICATION_PORT_VALUE_RANGE.get(
                String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      } else if (!canUseAsPort(replicationPort))
      {
        if (isPriviledgedPort(replicationPort))
        {
          errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
                  String.valueOf(replicationPort)));
        } else
        {
          errorMsgs.add(INFO_CANNOT_BIND_PORT.get(
                  String.valueOf(replicationPort)));
        }
        qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);

      } else
      {
        /* Check that we did not chose this port for another protocol */
        SecurityOptions sec = getUserData().getSecurityOptions();
        if ((replicationPort == getUserData().getServerPort()) ||
            (replicationPort == getUserData().getServerJMXPort()) ||
            ((replicationPort == sec.getSslPort()) && sec.getEnableSSL()))
        {
          errorMsgs.add(
              INFO_REPLICATION_PORT_ALREADY_CHOSEN_FOR_OTHER_PROTOCOL.get());
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
        }
        else
        {
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, false);
        }
      }

    } catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_REPLICATION_PORT_VALUE_RANGE.get(
              String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE)));
      qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
    }
    return replicationPort;
  }

  private void checkRemoteHostPortDnAndPwd(String host, String sPort, String dn,
      String pwd, QuickSetup qs, ArrayList<Message> errorMsgs)
  {
    // Check host
    if ((host == null) || (host.length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_HOST.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, false);
    }

    // Check port
    try
    {
      Integer.parseInt(sPort);
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, false);
    }
    catch (Throwable t)
    {
      errorMsgs.add(INFO_INVALID_REMOTE_PORT.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
    }

    // Check dn
    if ((dn == null) || (dn.length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_DN.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, false);
    }

    // Check password
    if ((pwd == null) || (pwd.length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_PWD.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, false);
    }
  }

  private void updateUserDataWithADS(String host, int port, String dn,
      String pwd, QuickSetup qs, ArrayList<Message> errorMsgs,
      boolean[] hasGlobalAdministrators,
      String[] effectiveDn) throws UserDataException
  {
    host = getHostNameForLdapUrl(host);
    String ldapUrl = "ldaps://"+host+":"+port;
    InitialLdapContext ctx = null;

    ApplicationTrustManager trustManager = getTrustManager();
    trustManager.setHost(host);
    trustManager.resetLastRefusedItems();
    try
    {
      effectiveDn[0] = dn;
      try
      {
        ctx = createLdapsContext(ldapUrl, dn, pwd,
            getConnectTimeout(), null, trustManager);
      }
      catch (Throwable t)
      {
        if (!isCertificateException(t))
        {
          // Try using a global administrator
          dn = ADSContext.getAdministratorDN(dn);
          effectiveDn[0] = dn;
          ctx = createLdapsContext(ldapUrl, dn, pwd,
              getConnectTimeout(), null, trustManager);
        }
        else
        {
          throw t;
        }
      }

      ADSContext adsContext = new ADSContext(ctx);
      if (adsContext.hasAdminData())
      {
        /* Check if there are already global administrators */
        Set<?> administrators = adsContext.readAdministratorRegistry();
        hasGlobalAdministrators[0] = administrators.size() > 0;
        Set<TopologyCacheException> exceptions =
        updateUserDataWithSuffixesInADS(adsContext, trustManager);
        Set<Message> exceptionMsgs = new LinkedHashSet<Message>();
        /* Check the exceptions and see if we throw them or not. */
        for (TopologyCacheException e : exceptions)
        {
          switch (e.getType())
          {
          case NOT_GLOBAL_ADMINISTRATOR:
            Message errorMsg = INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get();
            throw new UserDataException(Step.REPLICATION_OPTIONS, errorMsg);
          case GENERIC_CREATING_CONNECTION:
            if ((e.getCause() != null) &&
                isCertificateException(e.getCause()))
            {
              UserDataCertificateException.Type excType;
              ApplicationTrustManager.Cause cause = null;
              if (e.getTrustManager() != null)
              {
                cause = e.getTrustManager().getLastRefusedCause();
              }
              LOG.log(Level.INFO, "Certificate exception cause: "+cause);
              if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
              {
                excType = UserDataCertificateException.Type.NOT_TRUSTED;
              }
              else if (cause ==
                ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
              {
                excType = UserDataCertificateException.Type.HOST_NAME_MISMATCH;
              }
              else
              {
                excType = null;
              }
              if (excType != null)
              {
                String h;
                int p;
                try
                {
                  URI uri = new URI(e.getLdapUrl());
                  h = uri.getHost();
                  p = uri.getPort();
                }
                catch (Throwable t)
                {
                  LOG.log(Level.WARNING,
                      "Error parsing ldap url of TopologyCacheException.", t);
                  h = INFO_NOT_AVAILABLE_LABEL.get().toString();
                  p = -1;
                }
                throw new UserDataCertificateException(
                        Step.REPLICATION_OPTIONS,
                        INFO_CERTIFICATE_EXCEPTION.get(
                                h, String.valueOf(p)),
                        e.getCause(), h, p,
                        e.getTrustManager().getLastRefusedChain(),
                        e.getTrustManager().getLastRefusedAuthType(), excType);
              }
            }
          }
          exceptionMsgs.add(getMessage(e));
        }
        if (exceptionMsgs.size() > 0)
        {
          Message confirmationMsg =
            INFO_ERROR_READING_REGISTERED_SERVERS_CONFIRM.get(
                    getMessageFromCollection(exceptionMsgs, "\n"));
          throw new UserDataConfirmationException(Step.REPLICATION_OPTIONS,
              confirmationMsg);
        }
      }
      else
      {
        updateUserDataWithSuffixesInServer(ctx);
      }
    }
    catch (UserDataException ude)
    {
      throw ude;
    }
    catch (Throwable t)
    {
      LOG.log(Level.INFO, "Error connecting to remote server.", t);
      if (isCertificateException(t))
      {
        UserDataCertificateException.Type excType;
        ApplicationTrustManager.Cause cause =
          trustManager.getLastRefusedCause();
        LOG.log(Level.INFO, "Certificate exception cause: "+cause);
        if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
        {
          excType = UserDataCertificateException.Type.NOT_TRUSTED;
        }
        else if (cause == ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
        {
          excType = UserDataCertificateException.Type.HOST_NAME_MISMATCH;
        }
        else
        {
          excType = null;
        }

        if (excType != null)
        {
          throw new UserDataCertificateException(Step.REPLICATION_OPTIONS,
              INFO_CERTIFICATE_EXCEPTION.get(host, String.valueOf(port)), t,
              host, port, trustManager.getLastRefusedChain(),
              trustManager.getLastRefusedAuthType(), excType);
        }
        else
        {
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
          errorMsgs.add(INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
                  host+":"+port, t.toString()));
        }
      }
      else if (t instanceof NamingException)
      {
        errorMsgs.add(Utils.getMessageForException((NamingException)t,
            host+":"+port));
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
        if (!(t instanceof NamingSecurityException))
        {
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
        }
      }
      else if (t instanceof ADSContextException)
      {
        errorMsgs.add(INFO_REMOTE_ADS_EXCEPTION.get(
                host+":"+port, t.toString()));
      }
      else
      {
        throw new UserDataException(Step.REPLICATION_OPTIONS,
            getThrowableMsg(INFO_BUG_MSG.get(), t));
      }
    }
    finally
    {
      StaticUtils.close(ctx);
    }
  }

  /**
   * Validate the data provided by the user in the create global administrator
   * panel and update the UserInstallData object according to that content.
   *
   * @throws
   *           UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForCreateAdministratorPanel(QuickSetup qs)
  throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    // Check the Global Administrator UID
    String uid = qs.getFieldStringValue(FieldName.GLOBAL_ADMINISTRATOR_UID);

    if ((uid == null) || (uid.trim().length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_ADMINISTRATOR_UID.get());
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_UID, true);
    }
    else
    {
      getUserData().setGlobalAdministratorUID(uid);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_UID, false);
    }

    // Check the provided passwords
    String pwd1 = qs.getFieldStringValue(FieldName.GLOBAL_ADMINISTRATOR_PWD);
    String pwd2 = qs.getFieldStringValue(
        FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM);
    if (pwd1 == null)
    {
      pwd1 = "";
    }

    boolean pwdValid = true;
    if (!pwd1.equals(pwd2))
    {
      errorMsgs.add(INFO_NOT_EQUAL_PWD.get());
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM, true);
      pwdValid = false;

    }
    if (pwd1.length() < MIN_DIRECTORY_MANAGER_PWD)
    {
      errorMsgs.add(INFO_PWD_TOO_SHORT.get(
              String.valueOf(MIN_DIRECTORY_MANAGER_PWD)));
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD, true);
      if ((pwd2 == null) || (pwd2.length() < MIN_DIRECTORY_MANAGER_PWD))
      {
        qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM,
            true);
      }
      pwdValid = false;
    }

    if (pwdValid)
    {
      getUserData().setGlobalAdministratorPassword(pwd1);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD, false);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM, false);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.CREATE_GLOBAL_ADMINISTRATOR,
          getMessageFromCollection(errorMsgs, "\n"));
    }
  }

  /**
   * Validate the data provided by the user in the replicate suffixes options
   * panel and update the UserInstallData object according to that content.
   *
   * @throws
   *           UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForSuffixesOptionsPanel(QuickSetup qs)
  throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();
    if (qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE_OPTIONS) ==
      SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES)
    {
      Set<?> s = (Set<?>)qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE);
      if (s.isEmpty())
      {
        errorMsgs.add(INFO_NO_SUFFIXES_CHOSEN_TO_REPLICATE.get());
        qs.displayFieldInvalid(FieldName.SUFFIXES_TO_REPLICATE, true);
      }
      else
      {
        Set<SuffixDescriptor> chosen = new HashSet<SuffixDescriptor>();
        for (Object o: s)
        {
          chosen.add((SuffixDescriptor)o);
        }
        qs.displayFieldInvalid(FieldName.SUFFIXES_TO_REPLICATE, false);
        Set<SuffixDescriptor> available = getUserData().
        getSuffixesToReplicateOptions().getAvailableSuffixes();

        SuffixesToReplicateOptions options =
          new SuffixesToReplicateOptions(
          SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES,
              available,
              chosen);
        getUserData().setSuffixesToReplicateOptions(options);
      }
      getUserData().setRemoteWithNoReplicationPort(
          getRemoteWithNoReplicationPort(getUserData()));
    }
    else
    {
      Set<SuffixDescriptor> available = getUserData().
      getSuffixesToReplicateOptions().getAvailableSuffixes();
      Set<SuffixDescriptor> chosen = getUserData().
      getSuffixesToReplicateOptions().getSuffixes();
      SuffixesToReplicateOptions options =
        new SuffixesToReplicateOptions(
            SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY,
            available,
            chosen);
      getUserData().setSuffixesToReplicateOptions(options);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.SUFFIXES_OPTIONS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
  }

  /**
   * Validate the data provided by the user in the remote server replication
   * port panel and update the userData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   */
  private void updateUserDataForRemoteReplicationPorts(QuickSetup qs)
      throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();
    Map<ServerDescriptor, AuthenticationData> servers =
      getUserData().getRemoteWithNoReplicationPort();
    Map<?, ?> hm =
      (Map<?, ?>) qs.getFieldValue(FieldName.REMOTE_REPLICATION_PORT);
    Map<?, ?> hmSecure =
      (Map<?, ?>) qs.getFieldValue(FieldName.REMOTE_REPLICATION_SECURE);
    for (ServerDescriptor server : servers.keySet())
    {
      String hostName = server.getHostName();
      boolean secureReplication = (Boolean)hmSecure.get(server.getId());
      String sPort = (String)hm.get(server.getId());
      try
      {
        int replicationPort = Integer.parseInt(sPort);
        if ((replicationPort < MIN_PORT_VALUE) ||
            (replicationPort > MAX_PORT_VALUE))
        {
          errorMsgs.add(INFO_INVALID_REMOTE_REPLICATION_PORT_VALUE_RANGE.get(
                  getHostPort(server),
                  String.valueOf(MIN_PORT_VALUE),
                  String.valueOf(MAX_PORT_VALUE)));
        }
        if (hostName.equalsIgnoreCase(getUserData().getHostName()))
        {
          int securePort = -1;
          if (getUserData().getSecurityOptions().getEnableSSL())
          {
            securePort = getUserData().getSecurityOptions().getSslPort();
          }
          if ((replicationPort == getUserData().getServerPort()) ||
              (replicationPort == getUserData().getServerJMXPort()) ||
              (replicationPort ==
                getUserData().getReplicationOptions().getReplicationPort()) ||
              (replicationPort == securePort))
          {
            errorMsgs.add(
                  INFO_REMOTE_REPLICATION_PORT_ALREADY_CHOSEN_FOR_OTHER_PROTOCOL
                          .get(getHostPort(server)));
          }
        }
        AuthenticationData authData = new AuthenticationData();
        authData.setPort(replicationPort);
        authData.setUseSecureConnection(secureReplication);
        servers.put(server, authData);
      } catch (NumberFormatException nfe)
      {
        errorMsgs.add(INFO_INVALID_REMOTE_REPLICATION_PORT_VALUE_RANGE.get(
                hostName, String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
      }
    }

    if (errorMsgs.size() > 0)
    {
      qs.displayFieldInvalid(FieldName.REMOTE_REPLICATION_PORT, true);
      throw new UserDataException(Step.REMOTE_REPLICATION_PORTS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_REPLICATION_PORT, false);
      getUserData().setRemoteWithNoReplicationPort(servers);
    }
  }

  /**
   * Validate the data provided by the user in the new suffix data options panel
   * and update the UserInstallData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForNewSuffixOptionsPanel(QuickSetup qs)
      throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    NewSuffixOptions dataOptions = null;

    // Check the base dn
    boolean validBaseDn = false;
    String baseDn = qs.getFieldStringValue(FieldName.DIRECTORY_BASE_DN);
    if ((baseDn == null) || (baseDn.trim().length() == 0))
    {
      // Do nothing, the user does not want to provide a base DN.
      baseDn = "";
    } else if (!isDn(baseDn))
    {
      errorMsgs.add(INFO_NOT_A_BASE_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, true);
    } else if (isConfigurationDn(baseDn))
    {
      errorMsgs.add(INFO_BASE_DN_IS_CONFIGURATION_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, true);
    } else
    {
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, false);
      validBaseDn = true;
    }

    if (baseDn.equals(""))
    {
      LinkedList<String> baseDns = new LinkedList<String>();
      dataOptions = NewSuffixOptions.createEmpty(baseDns);
    }
    else
    {
      // Check the data options
      NewSuffixOptions.Type type =
        (NewSuffixOptions.Type) qs.getFieldValue(FieldName.DATA_OPTIONS);

      switch (type)
      {
      case IMPORT_FROM_LDIF_FILE:
        String ldifPath = qs.getFieldStringValue(FieldName.LDIF_PATH);
        if ((ldifPath == null) || (ldifPath.trim().equals("")))
        {
          errorMsgs.add(INFO_NO_LDIF_PATH.get());
          qs.displayFieldInvalid(FieldName.LDIF_PATH, true);
        } else if (!fileExists(ldifPath))
        {
          errorMsgs.add(INFO_LDIF_FILE_DOES_NOT_EXIST.get());
          qs.displayFieldInvalid(FieldName.LDIF_PATH, true);
        } else if (validBaseDn)
        {
          LinkedList<String> baseDns = new LinkedList<String>();
          baseDns.add(baseDn);
          LinkedList<String> ldifPaths = new LinkedList<String>();
          ldifPaths.add(ldifPath);

          dataOptions = NewSuffixOptions.createImportFromLDIF(
              baseDns, ldifPaths, null, null);
          qs.displayFieldInvalid(FieldName.LDIF_PATH, false);
        }
        break;

      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        // variable used to know if everything went ok during these
        // checks
        int startErrors = errorMsgs.size();

        // Check the number of entries
        String nEntries = qs.getFieldStringValue(FieldName.NUMBER_ENTRIES);
        if ((nEntries == null) || (nEntries.trim().equals("")))
        {
          errorMsgs.add(INFO_NO_NUMBER_ENTRIES.get());
          qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, true);
        } else
        {
          boolean nEntriesValid = false;
          try
          {
            int n = Integer.parseInt(nEntries);

            nEntriesValid = n >= MIN_NUMBER_ENTRIES && n <= MAX_NUMBER_ENTRIES;
          } catch (NumberFormatException nfe)
          { /* do nothing */
          }

          if (!nEntriesValid)
          {
            errorMsgs.add(INFO_INVALID_NUMBER_ENTRIES_RANGE.get(
                String.valueOf(MIN_NUMBER_ENTRIES),
                String.valueOf(MAX_NUMBER_ENTRIES)));
            qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, true);
          } else
          {
            qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, false);
          }
        }
        if (startErrors == errorMsgs.size() && validBaseDn)
        {
          // No validation errors
          LinkedList<String> baseDns = new LinkedList<String>();
          baseDns.add(baseDn);
          dataOptions = NewSuffixOptions.createAutomaticallyGenerated(baseDns,
              Integer.parseInt(nEntries));
        }
        break;

      default:
        qs.displayFieldInvalid(FieldName.LDIF_PATH, false);
        qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, false);
        if (validBaseDn)
        {
          LinkedList<String> baseDns = new LinkedList<String>();
          baseDns.add(baseDn);
          if (type == NewSuffixOptions.Type.CREATE_BASE_ENTRY)
          {
            dataOptions = NewSuffixOptions.createBaseEntry(baseDns);
          }
          else
          {
            dataOptions = NewSuffixOptions.createEmpty(baseDns);
          }
        }
      }
    }

    if (dataOptions != null)
    {
      getUserData().setNewSuffixOptions(dataOptions);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.NEW_SUFFIX_OPTIONS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
  }


  /**
   * Update the userData object according to the content of the runtime options
   * panel.
   *
   */
  private void updateUserDataForRuntimeOptionsPanel(QuickSetup qs)
  {
    getUserData().setJavaArguments(UserData.SERVER_SCRIPT_NAME,
        ((JavaArguments)qs.getFieldValue(FieldName.SERVER_JAVA_ARGUMENTS)));
    getUserData().setJavaArguments(UserData.IMPORT_SCRIPT_NAME,
        ((JavaArguments)qs.getFieldValue(FieldName.IMPORT_JAVA_ARGUMENTS)));
  }

  /**
   * Update the userData object according to the content of the review
   * panel.
   *
   */
  private void updateUserDataForReviewPanel(QuickSetup qs)
  {
    Boolean b = (Boolean) qs.getFieldValue(FieldName.SERVER_START_INSTALLER);
    getUserData().setStartServer(b);
    b = (Boolean) qs.getFieldValue(FieldName.ENABLE_WINDOWS_SERVICE);
    getUserData().setEnableWindowsService(b);
  }

  /**
   * Returns the number of free disk space in bytes required to install Open DS
   *
   * For the moment we just return 20 Megabytes. TODO we might want to have
   * something dynamic to calculate the required free disk space for the
   * installation.
   *
   * @return the number of free disk space required to install Open DS.
   */
  private long getRequiredInstallSpace()
  {
    return 20 * 1024 * 1024;
  }


  /**
   * Update the UserInstallData with the contents we discover in the ADS.
   */
  private Set<TopologyCacheException> updateUserDataWithSuffixesInADS(
      ADSContext adsContext, ApplicationTrustManager trustManager)
  throws TopologyCacheException
  {
    Set<TopologyCacheException> exceptions =
      new HashSet<TopologyCacheException>();
    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();
    SuffixesToReplicateOptions.Type type;

    if ((suf == null) || (suf.getType() ==
      SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE))
    {
      type = SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE;
    }
    else
    {
      type = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
    }
    lastLoadedCache = new TopologyCache(adsContext, trustManager,
        getConnectTimeout());
    LinkedHashSet<PreferredConnection> cnx =
      new LinkedHashSet<PreferredConnection>();
    cnx.add(PreferredConnection.getPreferredConnection(
        adsContext.getDirContext()));
    // We cannot use getPreferredConnections since the user data has not been
    // updated yet.
    lastLoadedCache.setPreferredConnections(cnx);
    lastLoadedCache.reloadTopology();
    Set<SuffixDescriptor> suffixes = lastLoadedCache.getSuffixes();
    Set<SuffixDescriptor> moreSuffixes = null;
    if (suf != null)
    {
      moreSuffixes = suf.getSuffixes();
    }
    getUserData().setSuffixesToReplicateOptions(
        new SuffixesToReplicateOptions(type, suffixes, moreSuffixes));

    /* Analyze if we had any exception while loading servers.  For the moment
     * only throw the exception found if the user did not provide the
     * Administrator DN and this caused a problem authenticating in one server
     * or if there is a certificate problem.
     */
    Set<ServerDescriptor> servers = lastLoadedCache.getServers();
    for (ServerDescriptor server : servers)
    {
      TopologyCacheException e = server.getLastException();
      if (e != null)
      {
        exceptions.add(e);
      }
    }
    return exceptions;
  }

  /**
   * Update the UserInstallData object with the contents of the server to which
   * we are connected with the provided InitialLdapContext.
   */
  private void updateUserDataWithSuffixesInServer(InitialLdapContext ctx)
  throws NamingException
  {
    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();
    SuffixesToReplicateOptions.Type type;
    Set<SuffixDescriptor> suffixes = new HashSet<SuffixDescriptor>();
    if (suf == null)
    {
      type = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
    }
    else
    {
      type = suf.getType();
    }

    ServerDescriptor s = ServerDescriptor.createStandalone(ctx,
        new TopologyCacheFilter());
    Set<ReplicaDescriptor> replicas = s.getReplicas();
    for (ReplicaDescriptor replica : replicas)
    {
      suffixes.add(replica.getSuffix());
    }
    Set<SuffixDescriptor> moreSuffixes = null;
    if (suf != null){
      moreSuffixes = suf.getSuffixes();
    }
    getUserData().setSuffixesToReplicateOptions(
        new SuffixesToReplicateOptions(type, suffixes, moreSuffixes));
  }

  /**
   * Returns the keystore path to be used for generating a self-signed
   * certificate.
   * @return the keystore path to be used for generating a self-signed
   * certificate.
   */
  protected String getSelfSignedKeystorePath()
  {
    String parentFile = getPath(getInstancePath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "keystore"));
  }

  /**
   * Returns the trustmanager path to be used for generating a self-signed
   * certificate.
   * @return the trustmanager path to be used for generating a self-signed
   * certificate.
   */
  private String getTrustManagerPath()
  {
    String parentFile = getPath(getInstancePath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "truststore"));
  }

  /**
   * Returns the path of the self-signed that we export to be able to create
   * a truststore.
   * @return the path of the self-signed that is exported.
   */
  private String getTemporaryCertificatePath()
  {
    String parentFile = getPath(getInstancePath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "server-cert.txt"));
  }

  /**
   * Returns the path to be used to store the password of the keystore.
   * @return the path to be used to store the password of the keystore.
   */
  private String getKeystorePinPath()
  {
    String parentFile = getPath(getInstancePath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "keystore.pin"));
  }


  /**
   * Returns the validity period to be used to generate the self-signed
   * certificate.
   * @return the validity period to be used to generate the self-signed
   * certificate.
   */
  private int getSelfSignedCertificateValidity()
  {
    return 20 * 365;
  }

  /**
   * Returns the Subject DN to be used to generate the self-signed certificate.
   * @return the Subject DN to be used to generate the self-signed certificate.
   */
  private String getSelfSignedCertificateSubjectDN()
  {
    return "cn="+Rdn.escapeValue(getUserData().getHostName())+
    ",O=OpenDJ Self-Signed Certificate";
  }

  /**
   * Returns the self-signed certificate password used for this session.  This
   * method calls <code>createSelfSignedCertificatePwd()</code> the first time
   * this method is called.
   * @return the self-signed certificate password used for this session.
   */
  protected String getSelfSignedCertificatePwd()
  {
    if (selfSignedCertPw == null) {
      selfSignedCertPw = SetupUtils.createSelfSignedCertificatePwd();
    }
    return new String(selfSignedCertPw);
  }

  private Map<ServerDescriptor, AuthenticationData>
  getRemoteWithNoReplicationPort(UserData userData)
  {
    Map<ServerDescriptor, AuthenticationData> servers =
      new HashMap<ServerDescriptor, AuthenticationData>();
    Set<SuffixDescriptor> suffixes =
      userData.getSuffixesToReplicateOptions().getSuffixes();
    for (SuffixDescriptor suffix : suffixes)
    {
      for (ReplicaDescriptor replica : suffix.getReplicas())
      {
        ServerDescriptor server = replica.getServer();
        Object v = server.getServerProperties().get(
            ServerDescriptor.ServerProperty.IS_REPLICATION_SERVER);
        if (!Boolean.TRUE.equals(v))
        {

          AuthenticationData authData = new AuthenticationData();
          authData.setPort(Constants.DEFAULT_REPLICATION_PORT);
          authData.setUseSecureConnection(false);
          servers.put(server, authData);
        }
      }
    }
    return servers;
  }

  private InitialLdapContext createLocalContext() throws NamingException
  {
    String ldapUrl = "ldaps://"+
    getHostNameForLdapUrl(getUserData().getHostName())+":"+
    getUserData().getAdminConnectorPort();
    String dn = getUserData().getDirectoryManagerDn();
    String pwd = getUserData().getDirectoryManagerPwd();
    return createLdapsContext(ldapUrl, dn, pwd,
        getConnectTimeout(), null, null);
  }

  /**
   * Gets an InitialLdapContext based on the information that appears on the
   * provided ServerDescriptor.
   * @param server the object describing the server.
   * @param trustManager the trust manager to be used to establish the
   * connection.
   * @param cnx the list of preferred LDAP URLs to be used to connect
   * to the server.
   * @return the InitialLdapContext to the remote server.
   * @throws ApplicationException if something goes wrong.
   */
  private InitialLdapContext getRemoteConnection(ServerDescriptor server,
      ApplicationTrustManager trustManager,
      LinkedHashSet<PreferredConnection> cnx)
  throws ApplicationException
  {
    Map<ADSContext.ServerProperty, Object> adsProperties;
    AuthenticationData auth =
      getUserData().getReplicationOptions().getAuthenticationData();
    if (!server.isRegistered())
    {
      /* Create adsProperties to be able to use the class ServerLoader to
       * get the connection.  Just update the connection parameters with what
       * the user chose in the Topology Options panel (i.e. even if SSL
       * is enabled on the remote server, use standard LDAP to connect to the
       * server if the user specified the LDAP port: this avoids having an
       * issue with the certificate if it has not been accepted previously
       * by the user).
       */
      adsProperties = new HashMap<ADSContext.ServerProperty, Object>();
      adsProperties.put(ADSContext.ServerProperty.HOST_NAME,
          server.getHostName());
      if (auth.useSecureConnection())
      {
        adsProperties.put(ADSContext.ServerProperty.LDAPS_PORT,
            String.valueOf(auth.getPort()));
        adsProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "true");
      }
      else
      {
        adsProperties.put(ADSContext.ServerProperty.LDAP_PORT,
            String.valueOf(auth.getPort()));
        adsProperties.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");
      }
      server.setAdsProperties(adsProperties);
    }
    return getRemoteConnection(server, auth.getDn(), auth.getPwd(),
        trustManager, getConnectTimeout(), cnx);
  }

  /**
   * Initializes a suffix with the contents of a replica that has a given
   * replication id.
   * @param ctx the connection to the server whose suffix we want to initialize.
   * @param replicaId the replication ID of the replica we want to use to
   * initialize the contents of the suffix.
   * @param suffixDn the dn of the suffix.
   * @param displayProgress whether we want to display progress or not.
   * @param sourceServerDisplay the string to be used to represent the server
   * that contains the data that will be used to initialize the suffix.
   * @throws ApplicationException if an unexpected error occurs.
   * @throws PeerNotFoundException if the replication mechanism cannot find
   * a peer.
   */
  public void initializeSuffix(InitialLdapContext ctx, int replicaId,
      String suffixDn, boolean displayProgress, String sourceServerDisplay)
  throws ApplicationException, PeerNotFoundException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-initialize-from-remote-replica");
    attrs.put(oc);
    attrs.put(
        "ds-task-class-name",
        "org.opends.server.tasks.InitializeTask");
    attrs.put("ds-task-initialize-domain-dn", suffixDn);
    attrs.put("ds-task-initialize-replica-server-id",
        String.valueOf(replicaId));
    while (!taskCreated)
    {
      checkAbort();
      String id = "quicksetup-initialize"+i;
      dn = "ds-task-id="+id+",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
        LOG.log(Level.WARNING, "A task with dn: "+dn+" already existed.");
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_LAUNCHING_INITIALIZATION.get(
                        sourceServerDisplay
                ), ne), ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-unprocessed-entry-count",
            "ds-task-processed-entry-count",
            "ds-task-log-message",
            "ds-task-state"
        });
    Message lastDisplayedMsg = null;
    String lastLogMsg = null;
    long lastTimeMsgDisplayed = -1;
    long lastTimeMsgLogged = -1;
    long totalEntries = 0;
    while (!isOver)
    {
      if (canceled)
      {
        // TODO: we should try to cleanly abort the initialize.  As we have
        // aborted the install, the server will be stopped and the remote
        // server will receive a connect error.
        checkAbort();
      }
      StaticUtils.sleep(500);
      if (canceled)
      {
        // TODO: we should try to cleanly abort the initialize.  As we have
        // aborted the install, the server will be stopped and the remote
        // server will receive a connect error.
        checkAbort();
      }
      try
      {
        NamingEnumeration<SearchResult> res =
          ctx.search(dn, filter, searchControls);
        SearchResult sr = null;
        try
        {
          while (res.hasMore())
          {
            sr = res.next();
          }
        }
        finally
        {
          res.close();
        }
        // Get the number of entries that have been handled and
        // a percentage...
        Message msg;
        String sProcessed = getFirstValue(sr,
        "ds-task-processed-entry-count");
        String sUnprocessed = getFirstValue(sr,
        "ds-task-unprocessed-entry-count");
        long processed = -1;
        long unprocessed = -1;
        if (sProcessed != null)
        {
          processed = Integer.parseInt(sProcessed);
        }
        if (sUnprocessed != null)
        {
          unprocessed = Integer.parseInt(sUnprocessed);
        }
        totalEntries = Math.max(totalEntries, processed+unprocessed);

        if ((processed != -1) && (unprocessed != -1))
        {
          if (processed + unprocessed > 0)
          {
            long perc = (100 * processed) / (processed + unprocessed);
            msg = INFO_INITIALIZE_PROGRESS_WITH_PERCENTAGE.get(sProcessed,
                String.valueOf(perc));
          }
          else
          {
            //msg = INFO_NO_ENTRIES_TO_INITIALIZE.get();
            msg = null;
          }
        }
        else if (processed != -1)
        {
          msg = INFO_INITIALIZE_PROGRESS_WITH_PROCESSED.get(sProcessed);
        }
        else if (unprocessed != -1)
        {
          msg = INFO_INITIALIZE_PROGRESS_WITH_UNPROCESSED.get(sUnprocessed);
        }
        else
        {
          msg = lastDisplayedMsg;
        }

        if (msg != null)
        {
          long currentTime = System.currentTimeMillis();
          /* Refresh period: to avoid having too many lines in the log */
          long minRefreshPeriod;
          if (totalEntries < 100)
          {
            minRefreshPeriod = 0;
          }
          else if (totalEntries < 1000)
          {
            minRefreshPeriod = 1000;
          }
          else if (totalEntries < 10000)
          {
            minRefreshPeriod = 5000;
          }
          else
          {
            minRefreshPeriod = 10000;
          }
          if (((currentTime - minRefreshPeriod) > lastTimeMsgLogged))
          {
            lastTimeMsgLogged = currentTime;
            LOG.log(Level.INFO, "Progress msg: "+msg);
          }
          if (displayProgress)
          {
            if (((currentTime - minRefreshPeriod) > lastTimeMsgDisplayed) &&
                !msg.equals(lastDisplayedMsg))
            {
              notifyListeners(getFormattedProgress(msg));
              lastDisplayedMsg = msg;
              notifyListeners(getLineBreak());
              lastTimeMsgDisplayed = currentTime;
            }
          }
        }

        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          LOG.log(Level.INFO, "Last task entry: "+sr);
          if (displayProgress && (msg != null) && !msg.equals(lastDisplayedMsg))
          {
            notifyListeners(getFormattedProgress(msg));
            lastDisplayedMsg = msg;
            notifyListeners(getLineBreak());
          }

          if (lastLogMsg == null)
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(
                    sourceServerDisplay, state, sourceServerDisplay);
          }
          else
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_LOG.get(
                    sourceServerDisplay, lastLogMsg, state,
                    sourceServerDisplay);
          }

          LOG.log(Level.WARNING, "Processed errorMsg: "+errorMsg);
          if (helper.isCompletedWithErrors(state))
          {
            if (displayProgress)
            {
              notifyListeners(getFormattedWarning(errorMsg));
            }
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            ApplicationException ae = new ApplicationException(
                ReturnCode.APPLICATION_ERROR, errorMsg,
                null);
            if ((lastLogMsg == null) ||
                helper.isPeersNotFoundError(lastLogMsg))
            {
              LOG.log(Level.WARNING, "Throwing peer not found error.  "+
                  "Last Log Msg: "+lastLogMsg);
              // Assume that this is a peer not found error.
              throw new PeerNotFoundException(errorMsg);
            }
            else
            {
              LOG.log(Level.SEVERE, "Throwing ApplicationException.");
              throw ae;
            }
          }
          else if (displayProgress)
          {
            LOG.log(Level.INFO, "Initialization completed successfully.");
            notifyListeners(getFormattedProgress(
                INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get()));
            notifyListeners(getLineBreak());
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
        LOG.log(Level.INFO, "Initialization entry not found.");
        if (displayProgress)
        {
          notifyListeners(getFormattedProgress(
            INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get()));
          notifyListeners(getLineBreak());
        }
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION.get(
                        sourceServerDisplay),
                        ne), ne);
      }
    }
    resetGenerationId(ctx, suffixDn, sourceServerDisplay);
  }

  /**
   * Returns the configuration file path to be used when invoking the
   * command-lines.
   * @return the configuration file path to be used when invoking the
   * command-lines.
   */
  private String getConfigurationFile()
  {
    return getPath(getInstallation().getCurrentConfigurationFile());
  }

  /**
   * Returns the configuration class name to be used when invoking the
   * command-lines.
   * @return the configuration class name to be used when invoking the
   * command-lines.
   */
  private String getConfigurationClassName()
  {
    return DEFAULT_CONFIG_CLASS_NAME;
  }

  private String getLocalReplicationServer()
  {
    return getUserData().getHostName()+":"+
    getUserData().getReplicationOptions().getReplicationPort();
  }

  private String getLocalHostPort()
  {
    return getUserData().getHostName()+":"+getUserData().getServerPort();
  }

  private void resetGenerationId(InitialLdapContext ctx,
      String suffixDn, String sourceServerDisplay)
  throws ApplicationException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-reset-generation-id");
    attrs.put(oc);
    attrs.put("ds-task-class-name",
        "org.opends.server.tasks.SetGenerationIdTask");
    attrs.put("ds-task-reset-generation-id-domain-base-dn", suffixDn);
    while (!taskCreated)
    {
      checkAbort();
      String id = "quicksetup-reset-generation-id-"+i;
      dn = "ds-task-id="+id+",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      { /* do nothing */
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_LAUNCHING_INITIALIZATION.get(
                        sourceServerDisplay
                ), ne), ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-log-message",
            "ds-task-state"
        });
    String lastLogMsg = null;
    while (!isOver)
    {
      StaticUtils.sleep(500);
      try
      {
        NamingEnumeration<SearchResult> res =
          ctx.search(dn, filter, searchControls);
        SearchResult sr = null;
        try
        {
          while (res.hasMore())
          {
            sr = res.next();
          }
        }
        finally
        {
          res.close();
        }
        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          if (lastLogMsg == null)
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(
                    sourceServerDisplay, state, sourceServerDisplay);
          }
          else
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_LOG.get(
                    sourceServerDisplay, lastLogMsg, state,
                    sourceServerDisplay);
          }

          if (helper.isCompletedWithErrors(state))
          {
            LOG.log(Level.WARNING, "Completed with error: "+errorMsg);
            notifyListeners(getFormattedWarning(errorMsg));
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            LOG.log(Level.WARNING, "Error: "+errorMsg);
            throw new ApplicationException(
                ReturnCode.APPLICATION_ERROR, errorMsg,
                null);
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION.get(
                        sourceServerDisplay),
                        ne), ne);
      }
    }
  }

  /**
   * Invokes a long operation in a separate thread and checks whether the user
   * canceled the operation or not.
   * @param thread the Thread that must be launched.
   * @throws ApplicationException if there was an error executing the task or
   * if the user canceled the installer.
   */
  private void invokeLongOperation(InvokeThread thread)
  throws ApplicationException
  {
    try
    {
      thread.start();
      while (!thread.isOver() && thread.isAlive())
      {
        if (canceled)
        {
          // Try to abort the thread
          try
          {
            thread.abort();
          }
          catch (Throwable t)
          {
            LOG.log(Level.WARNING, "Error cancelling thread: "+t, t);
          }
        }
        else if (thread.getException() != null)
        {
          throw thread.getException();
        }
        else
        {
          StaticUtils.sleep(100);
        }
      }
      if (thread.getException() != null)
      {
        throw thread.getException();
      }
      if (canceled)
      {
        checkAbort();
      }
    }
    catch (ApplicationException e)
    {
      LOG.log(Level.SEVERE, "Error: "+e, e);
      throw e;
    }
    catch (Throwable t)
    {
      LOG.log(Level.SEVERE, "Error: "+t, t);
      throw new ApplicationException(ReturnCode.BUG,
          Utils.getThrowableMsg(INFO_BUG_MSG.get(), t), t);
    }
  }


  /**
   * Returns the host port representation of the server to be used in progress
   * and error messages.  It takes into account the fact the host and port
   * provided by the user in the replication options panel.
   * NOTE: the code assumes that the user data with the contents of the
   * replication options has already been updated.
   * @param server the ServerDescriptor.
   * @return the host port string representation of the provided server.
   */
  protected String getHostPort(ServerDescriptor server)
  {
    String hostPort = null;

    for (PreferredConnection connection : getPreferredConnections())
    {
      String url = connection.getLDAPURL();
      if (url.equals(server.getLDAPURL()))
      {
        hostPort = server.getHostPort(false);
      }
      else if (url.equals(server.getLDAPsURL()))
      {
        hostPort = server.getHostPort(true);
      }
    }
    if (hostPort == null)
    {
      hostPort = server.getHostPort(true);
    }
    return hostPort;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void applicationPrintStreamReceived(String message)
  {
    InstallerHelper helper = new InstallerHelper();
    String parsedMessage = helper.getImportProgressMessage(message);
    if (parsedMessage != null)
    {
      lastImportProgress = parsedMessage;
    }
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.
   * @return the timeout to be used to connect in milliseconds.  Returns
   * {@code 0} if there is no timeout.
   */
  protected int getConnectTimeout()
  {
    return getUserData().getConnectTimeout();
  }


  /**
   * Copies the template instance files into the instance directory.
   *
   * @throws ApplicationException
   *           If an IO error occurred.
   */
  private void copyTemplateInstance() throws ApplicationException
  {
    FileManager fileManager = new FileManager();
    fileManager.synchronize(getInstallation().getTemplateDirectory(),
        getInstallation().getInstanceDirectory());
  }
}

/**
 * Class used to be able to cancel long operations.
 */
abstract class InvokeThread extends Thread implements Runnable
{
  protected boolean isOver = false;
  protected ApplicationException ae;

  /**
   * Returns <CODE>true</CODE> if the thread is over and <CODE>false</CODE>
   * otherwise.
   * @return  <CODE>true</CODE> if the thread is over and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isOver()
  {
    return isOver;
  }

  /**
   * Returns the exception that was encountered running the thread.
   * @return the exception that was encountered running the thread.
   */
  public ApplicationException getException()
  {
    return ae;
  }

  /**
   * Runnable implementation.
   */
  @Override
  public abstract void run();

  /**
   * Abort this thread.
   */
  public abstract void abort();
}
