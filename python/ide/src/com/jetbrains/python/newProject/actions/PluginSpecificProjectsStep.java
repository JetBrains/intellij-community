/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.actions.ProjectSpecificAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.platform.CustomStepProjectGenerator;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.NullableConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PluginSpecificProjectsStep extends DefaultActionGroup implements DumbAware {

  public PluginSpecificProjectsStep(@NotNull final NullableConsumer<ProjectSettingsStepBase> callback,
                                    @NotNull final List<DirectoryProjectGenerator> projectGenerators) {
    super("Plugin-specific", true);
    getTemplatePresentation().setIcon(AllIcons.Nodes.PluginLogo);
    for (DirectoryProjectGenerator generator : projectGenerators) {
      ProjectSettingsStepBase step = generator instanceof CustomStepProjectGenerator ?
                                     ((ProjectSettingsStepBase)((CustomStepProjectGenerator)generator).createStep(generator, callback)) :
                                     new ProjectSpecificSettingsStep(generator, callback);

      ProjectSpecificAction action = new ProjectSpecificAction(generator, step);
      addAll(action.getChildren(null));
    }
  }
}
