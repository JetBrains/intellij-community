// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.make;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class FormTypeRegistrar implements StartupActivity.DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    CompilerManager.getInstance(project).addCompilableFileType(StdFileTypes.GUI_DESIGNER_FORM);
  }
}