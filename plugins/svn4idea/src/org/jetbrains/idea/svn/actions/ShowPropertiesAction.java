package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SetPropertyDialog;
import org.jetbrains.idea.svn.dialogs.PropertiesComponent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;

public class ShowPropertiesAction extends BasicAction {

  protected String getActionName(AbstractVcs vcs) {
    return "Show Properties";
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    return true;
  }

  protected boolean needsFiles() {
    return false;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context, AbstractVcsHelper helper)
    throws VcsException {
    batchPerform(project, activeVcs, new VirtualFile[]{file}, context, helper);
  }

  protected void batchPerform(Project project, final SvnVcs activeVcs, VirtualFile[] file, DataContext context, AbstractVcsHelper helper) throws VcsException {
    final File[] ioFiles = new File[file.length];
    for (int i = 0; i < ioFiles.length; i++) {
      ioFiles[i] = new File(file[i].getPath());
    }
    if (ioFiles.length > 0) {
      ToolWindow w = ToolWindowManager.getInstance(project).getToolWindow(PropertiesComponent.ID);
      PropertiesComponent component = null;
      if (w == null) {
        component = new PropertiesComponent();
        w = ToolWindowManager.getInstance(project).registerToolWindow(PropertiesComponent.ID, component, ToolWindowAnchor.BOTTOM);
      } else {
        component = ((PropertiesComponent) w.getComponent());
      }
      w.setTitle(ioFiles[0].getName());
      w.show(null);
      final PropertiesComponent comp = component;
      w.activate(new Runnable() {
        public void run() {
          comp.setFile(activeVcs, ioFiles[0]);
        }
      });
    }

  }

  protected boolean isBatchAction() {
    return false;
  }
}
