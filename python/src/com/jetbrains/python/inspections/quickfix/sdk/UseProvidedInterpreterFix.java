// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix.sdk;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration;
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension;
import org.jetbrains.annotations.NotNull;

public class UseProvidedInterpreterFix implements LocalQuickFix {

  @NotNull
  private final Module myModule;

  @NotNull
  private final PyProjectSdkConfigurationExtension myExtension;

  @NotNull
  @IntentionName
  private final String myName;

  public UseProvidedInterpreterFix(@NotNull Module module,
                                    @NotNull PyProjectSdkConfigurationExtension extension,
                                    @NotNull @IntentionName String name) {
    myModule = module;
    myExtension = extension;
    myName = name;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter");
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return myName;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyProjectSdkConfiguration.INSTANCE.configureSdkUsingExtension(myModule, myExtension,
                                                                  () -> myExtension.createAndAddSdkForInspection(myModule));
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    // The quick fix doesn't change the code and is suggested on a file level
    return IntentionPreviewInfo.EMPTY;
  }
}