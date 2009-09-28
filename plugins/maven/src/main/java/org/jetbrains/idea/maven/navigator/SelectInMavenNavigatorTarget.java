package org.jetbrains.idea.maven.navigator;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class SelectInMavenNavigatorTarget implements SelectInTarget {
  public boolean canSelect(SelectInContext context) {
    return getMavenProject(context) != null;
  }

  public void selectIn(final SelectInContext context, boolean requestFocus) {
    Runnable r = new Runnable() {
      public void run() {
        MavenProjectsNavigator.getInstance(context.getProject()).selectInTree(getMavenProject(context));
      }
    };
    if (requestFocus) {
      ToolWindowManager.getInstance(context.getProject()).getToolWindow(getToolWindowId()).activate(r);
    }
    else {
      r.run();
    }
  }

  private MavenProject getMavenProject(SelectInContext context) {
    VirtualFile file = context.getVirtualFile();
    MavenProjectsManager manager = MavenProjectsManager.getInstance(context.getProject());
    Module module = ProjectRootManager.getInstance(context.getProject()).getFileIndex().getModuleForFile(file);
    return module == null ? null : manager.findProject(module);
  }

  public String getToolWindowId() {
    return MavenProjectsNavigator.TOOL_WINDOW_ID;
  }

  @Override
  public String toString() {
    return MavenProjectsNavigator.TOOL_WINDOW_ID;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return 20;
  }
}
