package com.intellij.idea;

import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ui.Splash;
import org.jdom.Element;

import java.io.IOException;


public class IdeaApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private Splash mySplash;
  private String[] myArgs;
  private boolean myPerformProjectLoad = true;
  private static IdeaApplication ourInstance;

  protected IdeaApplication(Splash splash, String[] args) {
    LOG.assertTrue(ourInstance == null);
    ourInstance = this;
    mySplash = splash;
    myArgs = args;

    boolean isInternal = "true".equals(System.getProperty("idea.is.internal"));
    ApplicationManagerEx.createApplication("componentSets/IdeaComponents", isInternal, false, "idea");
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

    registerActions();

    // Event queue should not be changed during initialization of application components.
    // It also cannot be changed before initialization of application components because IdeEventQueue uses other
    // application components. So it is proper to perform replacement only here.
    app.setupIdeQueue(IdeEventQueue.getInstance());

    app.invokeLater(new Runnable() {
      public void run() {
        mySplash.dispose();
        mySplash = null; // Allow GC collect the splash window

        if (myPerformProjectLoad) {
          loadProject();
        }
      }
    }, ModalityState.NON_MMODAL);
  }

  private void registerActions() {
    final PluginDescriptor[] plugins = PluginManager.getPlugins();
    for (int i = 0; i < plugins.length; i++) {
      PluginDescriptor plugin = plugins[i];

      final Element e = plugin.getActionsDescriptionElement();
      if (e != null) {
        ActionManagerEx.getInstanceEx().processActionsElement(e, plugin.getLoader());
      }
    }
  }

  private void loadProject() {
    GeneralSettings generalSettings = GeneralSettings.getInstance();

    String projectFile = null;
    if (myArgs != null && myArgs.length > 0) {
      if (myArgs[0] != null && myArgs[0].endsWith(".ipr")) {
        projectFile = myArgs[0];
      }
    }

    if (projectFile != null) {
      ProjectUtil.openProject(projectFile, null, false);
    }else if (generalSettings.isReopenLastProject()) {
      String lastProjectPath = RecentProjectsManager.getInstance().getLastProjectPath();
      if (lastProjectPath != null) {
        ProjectUtil.openProject(lastProjectPath, null, false);
      }else{
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
    }
  }

  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void setPerformProjectLoad(boolean performProjectLoad) {
    myPerformProjectLoad = performProjectLoad;
  }
}
