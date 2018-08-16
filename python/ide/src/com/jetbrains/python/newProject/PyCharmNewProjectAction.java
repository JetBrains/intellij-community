// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class PyCharmNewProjectAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final PyCharmNewProjectDialog dlg = new PyCharmNewProjectDialog();
    dlg.show();
  }
}
