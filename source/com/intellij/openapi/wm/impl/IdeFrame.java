package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.ImageLoader;

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
  private ActionManager myActionManager;

  public IdeFrame(ApplicationInfoEx applicationInfoEx,
                  ActionManager actionManager,
                  UISettings uiSettings,
                  DataManager dataManager,
                  KeymapManager keymapManager) {
    super(applicationInfoEx.getFullApplicationName());

    setRootPane(new IdeRootPane(actionManager, uiSettings, dataManager, keymapManager));

    final Image image = ImageLoader.loadFromResource("/icon.png");
    setIconImage(image);
    final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    setBounds(10, 10, screenSize.width - 20, screenSize.height - 40);

    myLayoutFocusTraversalPolicy = new LayoutFocusTraversalPolicyExt();
    setFocusTraversalPolicy(myLayoutFocusTraversalPolicy);

    setupCloseAction();
    myActionManager = actionManager;
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
                        ProjectUtil.closeProject(myProject);
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

  public void setTitle(final String title) {
    myTitle = title;
    updateTitle();
  }

  protected void setFrameTitle(final String text) {
    super.setTitle(text);
  }

  public void setFileTitle(final VirtualFile file) {
    myFileTitle = file != null ? calcFileTitle(file) : null;
    updateTitle();
  }

  private String calcFileTitle(final VirtualFile file) {
    final String url = file.getPresentableUrl();
    if (myProject == null) {
      return url;
    }
    else {
      final Module module = VfsUtil.getModuleForFile(myProject, file);
      if (module == null) return url;
      return new StringBuffer().append("[").append(module.getName()).append("] - ").append(url).toString();
    }
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
      return myProject;
    }
    return null;
  }

  public void setProject(final Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }
}
