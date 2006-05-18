package com.intellij.idea;

import com.incors.plaf.alloy.*;
import com.intellij.ExtensionPoints;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
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
  @NonNls private static final String IPR_SUFFIX = ".ipr";

  protected IdeaApplication(String[] args) {
    LOG.assertTrue(ourInstance == null);
    ourInstance = this;
    myArgs = args;
    boolean isInternal = Boolean.valueOf(System.getProperty(IDEA_IS_INTERNAL_PROPERTY)).booleanValue();
    @NonNls final String inspectAppCode = "inspect";
    @NonNls final String diffAppCode = "diff";
    final boolean isHeadless = myArgs.length > 0 && (Comparing.strEqual(myArgs[0], inspectAppCode) || Comparing.strEqual(myArgs[0], diffAppCode));
    if (isHeadless){
      new CommandLineApplication(isInternal, false, "componentSets/IdeaComponents");
    } else {
      ApplicationManagerEx.createApplication("componentSets/IdeaComponents", isInternal, false, false, "idea");
    }

    myStarter = getStarter();
    myStarter.premain(args);
  }

  private ApplicationStarter getStarter() {
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
      final Splash splash = new Splash(ApplicationInfoImpl.getShadowInstance().getLogoUrl());
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          splash.show();
        }
      });
      mySplash = splash;
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
          mySplash.dispose();
          mySplash = null; // Allow GC collect the splash window

          if (myPerformProjectLoad) {
            loadProject();
          }
        }
      }, ModalityState.NON_MMODAL);

      app.invokeLater(new Runnable() {
        public void run() {
          if (UpdateChecker.isMyVeryFirstOpening() &&
              UpdateChecker.checkNeeded()) {
            try {
              UpdateChecker.setMyVeryFirstOpening(false);
              final UpdateChecker.NewVersion newVersion = UpdateChecker.checkForUpdates();
              if (newVersion != null) {
                UpdateChecker.showUpdateInfoDialog(true, newVersion);
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

    String projectFile = null;
    if (myArgs != null && myArgs.length > 0) {
      if (myArgs[0] != null && myArgs[0].endsWith(IPR_SUFFIX)) {
        projectFile = myArgs[0];
      }
    }

    if (projectFile != null) {
      ProjectUtil.openProject(projectFile, null, false);
    }else if (generalSettings.isReopenLastProject()) {
      String lastProjectPath = RecentProjectsManager.getInstance().getLastProjectPath();
      if (lastProjectPath != null) {
        ProjectUtil.openProject(lastProjectPath, null, false);
      }
/* The below part is commented since instead of New Project Wizard the Welcome Screen will appear (appropriate code is added in IdeRootPane.java)
      else{
        // This is VERY BAD code. IONA wants to open their project wizard
        // on startup. They replace NEW_PROJECT action and we have to invoke
        // this action by ID. PLEASE, DO NOT COPY THIS CODE, DO NOT INVOKE
        // ACTION BY THIS WAY!!!!!!!!!!!
        AnAction newProjectAction=ActionManager.getInstance().getAction(IdeActions.ACTION_NEW_PROJECT);
        AnActionEvent event=new AnActionEvent(
          null,
          DataManager.getInstance().getDataContext(),
          ActionPlaces.MAIN_MENU,
          newProjectAction.getTemplatePresentation(),
          ActionManager.getInstance(),
          0
        );
        newProjectAction.update(event);
        if(newProjectAction.getTemplatePresentation().isEnabled()){
          newProjectAction.actionPerformed(event);
        }
      }
*/
      }
  }

  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void setPerformProjectLoad(boolean performProjectLoad) {
    myPerformProjectLoad = performProjectLoad;
  }
}
