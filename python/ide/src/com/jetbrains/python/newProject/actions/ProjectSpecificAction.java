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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.NullableConsumer;
import org.jetbrains.annotations.NotNull;

public class ProjectSpecificAction extends DefaultActionGroup implements DumbAware {

  private final ProjectSpecificSettingsStep mySettings;

  public ProjectSpecificAction(@NotNull final NullableConsumer<AbstractProjectSettingsStep> callback,
                               @NotNull final DirectoryProjectGenerator projectGenerator) {
    super(projectGenerator.getName(), true);
    getTemplatePresentation().setIcon(projectGenerator.getLogo());
    mySettings = new ProjectSpecificSettingsStep(projectGenerator, callback);
    add(mySettings);
  }

  public Sdk getSdk() {
    return mySettings.getSdk();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    super.actionPerformed(e);
  }
}
