// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

public class ReimportAction extends MavenProjectsManagerAction {
  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    return MavenActionUtil.hasProject(e.getDataContext());
  }

  @Override
  protected void perform(@NotNull MavenProjectsManager manager) {
    if (MavenUtil.isProjectTrustedEnoughToImport(manager.getProject())) {
      FileDocumentManager.getInstance().saveAllDocuments();
      manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    }
  }
}
