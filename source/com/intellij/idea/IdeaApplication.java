package com.intellij.idea;

import com.incors.plaf.alloy.*;
import com.intellij.ExtensionPoints;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.ui.Splash;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.IOException;


@SuppressWarnings({"CallToPrintStackTrace"})
public class IdeaApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private String[] myArgs;
  private boolean myPerformProjectLoad = true;
  private static IdeaApplication ourInstance;
  private ApplicationStarter myStarter;
  @NonNls public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";
  @NonNls public static final String IPR_SUFFIX = ".ipr";

  public IdeaApplication(String[] args) {
    LOG.assertTrue(ourInstance == null);
    ourInstance = this;
    myArgs = args;
    boolean isInternal = Boolean.valueOf(System.getProperty(IDEA_IS_INTERNAL_PROPERTY)).booleanValue();
    if (Main.isHeadless(args)) {
      System.setProperty("java.awt.headless", Boolean.TRUE.toString());
    }
    if (Main.isCommandLine(args)) {
      new CommandLineApplication(isInternal, false, Main.isHeadless(args), "componentSets/IdeaComponents");
    }
    else {
      ApplicationManagerEx.createApplication("componentSets/IdeaComponents", isInternal, false, false, false, "idea");
    }

    myStarter = getStarter();
    myStarter.premain(args);
  }

  protected ApplicationStarter getStarter() {
    if (myArgs.length > 0) {
      final Application app = ApplicationManager.getApplication();
      app.getPlugins(); //TODO[max] make it clearer plugins should initialize before querying for extpoints.

      final Object[] starters = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.APPLICATION_STARTER).getExtensions();
      String key = myArgs[0];
      for (Object o : starters) {
        ApplicationStarter starter = (ApplicationStarter)o;
        if (Comparing.equal(starter.getCommandName(), key)) return starter;
      }
    }
    return new IdeStarter();
  }

  public static IdeaApplication getInstance() {
    return ourInstance;
  }

  public void run() {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    try {
      app.load(PathManager.getOptionsPath());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (InvalidDataException e) {
      e.printStackTrace();
    }

    myStarter.main(myArgs);
    myStarter = null; //GC it
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initAlloy() {
    AlloyLookAndFeel.setProperty("alloy.licenseCode", "4#JetBrains#1ou2uex#6920nk");
    AlloyLookAndFeel.setProperty("alloy.isToolbarEffectsEnabled", "false");
    if (SystemInfo.isWindows) {
      UIManager.installLookAndFeel(AlloyIdea.NAME, AlloyIdea.class.getName());
    }
    UIManager.installLookAndFeel(AlloyDefault.NAME, AlloyDefault.class.getName());
    UIManager.installLookAndFeel(AlloyBedouin.NAME, AlloyBedouin.class.getName());
    UIManager.installLookAndFeel(AlloyAcid.NAME, AlloyAcid.class.getName());
    UIManager.installLookAndFeel(AlloyGlass.NAME, AlloyGlass.class.getName());

    if (SystemInfo.isMac) {
      UIManager.put("Panel.opaque", Boolean.TRUE);
      UIManager.installLookAndFeel("Quaqua", "ch.randelshofer.quaqua.QuaquaLookAndFeel");
    }
  }

  private class IdeStarter implements ApplicationStarter {
    private Splash mySplash;
    public String getCommandName() {
      return null;
    }

    public void premain(String[] args) {
      if (MainImpl.shouldShowSplash(args)) {
        final Splash splash = new Splash(ApplicationInfoImpl.getShadowInstance().getLogoUrl());
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            splash.show();
          }
        });
        mySplash = splash;
      }
      initAlloy();
    }

    public void main(String[] args) {

      // Event queue should not be changed during initialization of application components.
      // It also cannot be changed before initialization of application components because IdeEventQueue uses other
      // application components. So it is proper to perform replacement only here.
      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      // app.setupIdeQueue(IdeEventQueue.getInstance());
      ((WindowManagerImpl)WindowManager.getInstance()).showFrame();

      app.invokeLater(new Runnable() {
        public void run() {
          if (mySplash != null) {
            mySplash.dispose();
            mySplash = null; // Allow GC collect the splash window
          }

          PluginManager.reportPluginError();

          if (myPerformProjectLoad) {
            loadProject();
          }
        }
      }, ModalityState.NON_MODAL);

      app.invokeLater(new Runnable() {
        public void run() {
          if (UpdateChecker.isMyVeryFirstOpening() && UpdateChecker.checkNeeded()) {
            try {
              UpdateChecker.setMyVeryFirstOpening(false);
              final UpdateChecker.NewVersion newVersion = UpdateChecker.checkForUpdates();
              final String updatedPlugins = UpdateChecker.updatePlugins();
              if (newVersion != null) {
                UpdateChecker.showUpdateInfoDialog(true, newVersion, updatedPlugins);
              } else if (updatedPlugins != null) {
                UpdateChecker.showNoUpdatesDialog(true, updatedPlugins);
              }
            }
            catch (ConnectionException e) {
              // It's not a problem on automatic check
            }
          }
        }
      });
    }
  }

  private void loadProject() {
    GeneralSettings generalSettings = GeneralSettings.getInstance();

    if (myArgs != null && myArgs.length > 0 && myArgs[0] != null) {
      if (ProjectUtil.openOrImport(myArgs[0], null, false) != null) {
        return;
      }
    }

    if (generalSettings.isReopenLastProject()) {
      String lastProjectPath = RecentProjectsManager.getInstance().getLastProjectPath();
      if (lastProjectPath != null) {
        ProjectUtil.openProject(lastProjectPath, null, false);
      }
    }
  }

  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void setPerformProjectLoad(boolean performProjectLoad) {
    myPerformProjectLoad = performProjectLoad;
  }
}
