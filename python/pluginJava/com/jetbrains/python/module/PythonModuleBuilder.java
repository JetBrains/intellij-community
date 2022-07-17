// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.module;

import com.intellij.ide.NewProjectWizardLegacy;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SdkSettingsStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class PythonModuleBuilder extends PythonModuleBuilderBase implements SourcePathsBuilder {
  private List<Pair<String, String>> mySourcePaths;

  @Override
  public List<Pair<String, String>> getSourcePaths() {
    return mySourcePaths;
  }

  @Override
  public void setSourcePaths(final List<Pair<String, String>> sourcePaths) {
    mySourcePaths = sourcePaths;
  }

  @Override
  public void addSourcePath(final Pair<String, String> sourcePathInfo) {
    if (mySourcePaths == null) {
      mySourcePaths = new ArrayList<>();
    }
    mySourcePaths.add(sourcePathInfo);
  }

  @Override
  public boolean isAvailable() {
    return NewProjectWizardLegacy.isAvailable();
  }

  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    return new SdkSettingsStep(settingsStep, this, id -> PythonSdkType.getInstance() == id) {
      @Override
      protected void onSdkSelected(Sdk sdk) {
        setSdk(sdk);
      }
    };
  }
}
