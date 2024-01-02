// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.sphinx;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.python.reStructuredText.RestPythonUtil;
import org.jetbrains.annotations.NotNull;

/**
 * user : catherine
 */
public class RunSphinxQuickStartAction extends AnAction implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    RestPythonUtil.updateSphinxQuickStartRequiredAction(event);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Presentation presentation = RestPythonUtil.updateSphinxQuickStartRequiredAction(e);
    assert presentation.isEnabled() && presentation.isVisible() : "Sphinx requirements for action are not satisfied";

    final Project project = e.getData(CommonDataKeys.PROJECT);

    if (project == null) return;

    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    if (module == null) {
      Module[] modules = ModuleManager.getInstance(project).getModules();
      module = modules.length == 0 ? null : modules [0];
    }

    if (module == null) return;
    final SphinxBaseCommand action = new SphinxBaseCommand();
    final Module finalModule = module;
    ApplicationManager.getApplication().invokeLater(() -> action.execute(finalModule), ModalityState.nonModal());
  }
}