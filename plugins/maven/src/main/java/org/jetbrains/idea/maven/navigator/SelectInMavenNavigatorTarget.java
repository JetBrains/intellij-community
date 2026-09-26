// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public final class SelectInMavenNavigatorTarget implements SelectInTarget, DumbAware {
  @Override
  public boolean canSelect(SelectInContext context) {
    return getMavenProject(context) != null;
  }

  @Override
  public void selectIn(final SelectInContext context, boolean requestFocus) {
    Runnable r = () -> MavenProjectsNavigator.getInstance(context.getProject()).selectInTree(getMavenProject(context));
    if (requestFocus) {
      ToolWindow window = ToolWindowManager.getInstance(context.getProject()).getToolWindow(getToolWindowId());
      if (window != null) {
        window.activate(r);
      }
    }
    else {
      r.run();
    }
  }

  private static MavenProject getMavenProject(SelectInContext context) {
    VirtualFile file = context.getVirtualFile();
    MavenProjectsManager manager = MavenProjectsManager.getInstance(context.getProject());
    Module module = ProjectRootManager.getInstance(context.getProject()).getFileIndex().getModuleForFile(file);
    return module == null ? null : manager.findProject(module);
  }

  @Override
  public String getToolWindowId() {
    return MavenProjectsNavigator.TOOL_WINDOW_ID;
  }

  @Override
  public String toString() {
    return MavenProjectBundle.message("maven.name");
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public float getWeight() {
    return 20;
  }
}
