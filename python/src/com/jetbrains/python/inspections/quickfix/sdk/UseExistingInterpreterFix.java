// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix.sdk;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration;
import com.jetbrains.python.ui.PyUiUtil;
import org.jetbrains.annotations.NotNull;

public class UseExistingInterpreterFix extends UseInterpreterFix<Sdk> {
  @NotNull
  private final Module myModule;

  public UseExistingInterpreterFix(@NotNull Sdk existingSdk, @NotNull Module module) {
    super(existingSdk);
    myModule = module;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyUiUtil.clearFileLevelInspectionResults(project);
    PyProjectSdkConfiguration.INSTANCE.setReadyToUseSdk(project, myModule, mySdk);
  }
}