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
package com.jetbrains.python.packaging.ui;

import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.packaging.PyRequirement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author vlan
 */
public class PyChooseRequirementsDialog extends ChooseElementsDialog<PyRequirement> {
  public PyChooseRequirementsDialog(@NotNull Project project, @NotNull List<? extends PyRequirement> requirements) {
    super(project, requirements, "Choose Packages to Install", "Choose one or more packages to install:");
  }

  @NotNull
  @Override
  protected String getItemText(@NotNull PyRequirement requirement) {
    return requirement.toString();
  }

  @Nullable
  @Override
  protected Icon getItemIcon(@NotNull PyRequirement requirement) {
    return null;
  }
}
