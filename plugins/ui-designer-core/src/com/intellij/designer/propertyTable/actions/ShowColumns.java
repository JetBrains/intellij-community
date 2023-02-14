/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable.actions;

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.propertyTable.RadPropertyTable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class ShowColumns extends ToggleAction {
  private final RadPropertyTable myTable;

  public ShowColumns(RadPropertyTable table) {
    myTable = table;

    Presentation presentation = getTemplatePresentation();
    String text = DesignerBundle.message("designer.properties.show.columns");
    presentation.setText(text);
    presentation.setDescription(text);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myTable.getTableHeader().isVisible();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    myTable.setShowColumns(state);
  }
}