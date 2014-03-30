/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author oleg
 */
public class PythonSdkSelectStep extends ModuleWizardStep {
  protected final PythonSdkChooserPanel myPanel;
  protected final PythonModuleBuilderBase mySettingsHolder;

  private final String myHelp;

  public PythonSdkSelectStep(@NotNull final PythonModuleBuilderBase settingsHolder,
                           @Nullable final String helpId,
                           @Nullable final Project project) {
    super();
    mySettingsHolder = settingsHolder;
    myPanel = new PythonSdkChooserPanel(project);
    myHelp = helpId;
  }

  public String getHelpId() {
    return myHelp;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  public JComponent getComponent() {
    return myPanel;
  }


  public void updateDataModel() {
    final Sdk sdk = getSdk();
    mySettingsHolder.setSdk(sdk);
  }

  @Nullable
  private Sdk getSdk() {
    return myPanel.getChosenJdk();
  }

  public Icon getIcon() {
    return ICON;
  }

  public boolean validate() {
    return true;
  }
}
