// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog;
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

  @SuppressWarnings("deprecation")
  @Override
  protected void perform(@NotNull MavenProjectsManager manager) {
    ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(manager.getProject(), MavenUtil.SYSTEM_ID);
    FileDocumentManager.getInstance().saveAllDocuments();
    manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
  }
}
