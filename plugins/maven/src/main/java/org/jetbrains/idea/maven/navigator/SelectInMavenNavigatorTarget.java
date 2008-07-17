package org.jetbrains.idea.maven.navigator;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.idea.maven.project.MavenConstants;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

public class SelectInMavenNavigatorTarget implements SelectInTarget {
  public boolean canSelect(SelectInContext context) {
    VirtualFile file = context.getVirtualFile();
    MavenProjectsManager manager = MavenProjectsManager.getInstance(context.getProject());

    if (!MavenConstants.POM_XML.equals(file.getName())) return false;
    if (!manager.isMavenizedProject()) return false;
    return manager.findProject(file) != null;
  }

  public void selectIn(final SelectInContext context, boolean requestFocus) {
    Runnable r = new Runnable() {
      public void run() {
        MavenProjectNavigator.getInstance(context.getProject()).selectInTree(context.getVirtualFile());
      }
    };
    if (requestFocus) {
      ToolWindowManager.getInstance(context.getProject()).getToolWindow(getToolWindowId()).activate(r);
    } else {
      r.run();
    }
  }

  public String getToolWindowId() {
    return MavenProjectNavigator.MAVEN_NAVIGATOR_TOOLWINDOW_ID;
  }

  @Override
  public String toString() {
    return MavenProjectNavigator.MAVEN_NAVIGATOR_TOOLWINDOW_ID;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return 20;
  }
}
