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
package com.jetbrains.python.templateLanguages;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
* @author yole
*/
public class ConfigureTemplateDirectoriesAction implements LocalQuickFix, LowPriorityAction {
  @NotNull
  @Override
  public String getFamilyName() {
    return "Configure template directories";
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Structure");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
