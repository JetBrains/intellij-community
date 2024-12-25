// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * New project wizard entry point
 */
final class PyCharmNewProjectAction extends DumbAwareAction {

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final PyCharmNewProjectDialog dlg = new PyCharmNewProjectDialog();
    dlg.show();
  }
}
