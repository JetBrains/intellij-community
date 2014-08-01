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
package com.jetbrains.python.newProject.actions;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.HideableDecorator;
import com.intellij.util.NullableConsumer;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ProjectSpecificSettingsStep extends AbstractProjectSettingsStep implements DumbAware {

  public ProjectSpecificSettingsStep(@NotNull final DirectoryProjectGenerator projectGenerator,
                                     @NotNull final NullableConsumer<AbstractProjectSettingsStep> callback) {
    super(projectGenerator, callback);
  }

  @Override
  @Nullable
  protected JPanel createAdvancedSettings() {
    JComponent advancedSettings = null;
    if (myProjectGenerator instanceof PythonProjectGenerator)
      advancedSettings = ((PythonProjectGenerator)myProjectGenerator).getSettingsPanel(myProjectDirectory);
    else if (myProjectGenerator instanceof WebProjectTemplate) {
      advancedSettings = ((WebProjectTemplate)myProjectGenerator).getPeer().getComponent();
    }
    if (advancedSettings != null) {
      final JPanel jPanel = new JPanel(new VerticalFlowLayout());
      final HideableDecorator deco = new HideableDecorator(jPanel, "Mor&e Settings", false);
      boolean isValid = checkValid();
      deco.setOn(!isValid);
      if (myProjectGenerator instanceof PythonProjectGenerator && !deco.isExpanded()) {
        final ValidationResult result = ((PythonProjectGenerator)myProjectGenerator).warningValidation(getSdk());
        deco.setOn(!result.isOk());
      }
      deco.setContentComponent(advancedSettings);
      return jPanel;
    }
    return null;
  }
}
