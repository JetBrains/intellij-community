package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made non-final for Fabrique
public class IdeFrame extends JFrame implements DataProvider {
  private String myTitle;
  private String myFileTitle;
  private Project myProject;
  private final LayoutFocusTraversalPolicyExt myLayoutFocusTraversalPolicy;

  private IdeRootPane myRootPane;

  public IdeFrame(ApplicationInfoEx applicationInfoEx,
                  ActionManager actionManager,
                  UISettings uiSettings,
                  DataManager dataManager,
                  KeymapManager keymapManager) {
    super(applicationInfoEx.getFullApplicationName());
    myRootPane = new IdeRootPane(actionManager, uiSettings, dataManager, keymapManager);
    setRootPane(myRootPane);

    UIUtil.updateFrameIcon(this);
    final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    setBounds(10, 10, screenSize.width - 20, screenSize.height - 40);

    myLayoutFocusTraversalPolicy = new LayoutFocusTraversalPolicyExt();
    setFocusTraversalPolicy(myLayoutFocusTraversalPolicy);

    setupCloseAction();
    new MnemonicHelper().register(this);
  }

  /**
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   *
   * THIS IS AN "ABSOLUTELY-GURU METHOD".
   * NOBODY SHOULD ADD OTHER USAGES OF IT :)
   * ONLY ANTON AND VOVA ARE PERMITTED TO USE THIS METHOD!!!
   *
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   */
  public final void setDefaultFocusableComponent(final JComponent component) {
    myLayoutFocusTraversalPolicy.setOverridenDefaultComponent(component);
  }

  /**
   * This is overriden to get rid of strange Alloy LaF customization of frames. For unknown reason it sets the maxBounds rectangle
   * and it does it plain wrong. Setting bounds to <code>null</code> means default value should be taken from the underlying OS.
   */
  public synchronized void setMaximizedBounds(Rectangle bounds) {
    super.setMaximizedBounds(null);
  }

// Made protected for Fabrique
  protected void setupCloseAction() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
              if (openProjects.length > 1) {
                if (myProject != null && myProject.isOpen()) {
                  ProjectUtil.closeProject(myProject);
                }
                RecentProjectsManager.getInstance().updateLastProjectPath();
              }
              else {
                ApplicationManagerEx.getApplicationEx().exit();
              }
            }
          }, ModalityState.NON_MMODAL);
        }
      }
    );
  }

  public StatusBarEx getStatusBar() {
    return ((IdeRootPane)getRootPane()).getStatusBar();
  }

  public void updateToolbar() {
    ((IdeRootPane)getRootPane()).updateToolbar();
  }

  public void updateMenuBar(){
    ((IdeRootPane)getRootPane()).updateMainMenuActions();
  }

  public void setTitle(final String title) {
    myTitle = title;
    updateTitle();
  }

  protected void setFrameTitle(final String text) {
    super.setTitle(text);
  }

  public void setFileTitle(final VirtualFile file) {
    myFileTitle = file != null ? VfsUtil.calcRelativeToProjectPath(file, myProject) : null;
    updateTitle();
  }

  private void updateTitle() {
    final StringBuffer sb = new StringBuffer();
    if (myTitle != null && myTitle.length() > 0) {
      sb.append(myTitle);
      sb.append(" - ");
    }
    if (myFileTitle != null && myFileTitle.length() > 0) {
      sb.append(myFileTitle);
      sb.append(" - ");
    }
    sb.append(((ApplicationInfoEx)ApplicationInfo.getInstance()).getFullApplicationName());
    setFrameTitle(sb.toString());
  }

  public Object getData(final String dataId) {
    if (DataConstants.PROJECT.equals(dataId)) {
      if (myProject != null) {
        return myProject.isInitialized() ? myProject : null;
      }
    }
    return null;
  }

  public void setProject(final Project project) {
    myProject = project;
    if (myProject != null) {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable(){
        public void run() {
          if (myRootPane != null) {
            myRootPane.installNavigationBar(project);
          }
        }
      });
    } else {
      if (myRootPane != null) { //already disposed
        myRootPane.deinstallNavigationBar();
      }
    }
  }

  public Project getProject() {
    return myProject;
  }

  public void dispose() {
    if (myRootPane != null) {
      final StatusBarImpl statusBar = ((StatusBarImpl)myRootPane.getStatusBar());
      if (statusBar != null) {
        statusBar.disposeListeners();
      }
      myRootPane = null;
    }
    super.dispose();
  }
}
