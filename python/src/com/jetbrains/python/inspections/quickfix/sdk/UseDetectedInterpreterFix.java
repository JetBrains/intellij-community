// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix.sdk;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.jetbrains.python.sdk.BasePySdkExtKt;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PyEditorNotificationProvider;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration;
import com.jetbrains.python.ui.PyUiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UseDetectedInterpreterFix extends UseInterpreterFix<PyDetectedSdk> {

  @NotNull
  private final List<Sdk> myExistingSdks;

  private final boolean myAssociate;

  @NotNull
  private final Module myModule;

  public UseDetectedInterpreterFix(@NotNull PyDetectedSdk detectedSdk,
                                    @NotNull List<Sdk> existingSdks,
                                    boolean associate,
                                    @NotNull Module module) {
    super(detectedSdk);
    myExistingSdks = existingSdks;
    myAssociate = associate;
    myModule = module;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyUiUtil.clearFileLevelInspectionResults(project);
    final Sdk newSdk = myAssociate
                       ? PySdkExtKt.setupAssociated(mySdk, myExistingSdks, BasePySdkExtKt.getBasePath(myModule))
                       : PySdkExtKt.setup(mySdk, myExistingSdks);
    if (newSdk == null) return;

    SdkConfigurationUtil.addSdk(newSdk);
    if (myAssociate) PySdkExtKt.associateWithModule(newSdk, myModule, null);
    PyProjectSdkConfiguration.INSTANCE.setReadyToUseSdk(project, myModule, newSdk);
  }
}