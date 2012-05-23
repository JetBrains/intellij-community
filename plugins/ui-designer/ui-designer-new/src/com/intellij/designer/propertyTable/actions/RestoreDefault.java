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
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;

/**
 * @author Alexander Lobas
 */
public class RestoreDefault extends AnAction implements IPropertyTableAction {
  public RestoreDefault() {
    Presentation presentation = getTemplatePresentation();
    String text = DesignerBundle.message("designer.properties.restore_default");
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(IconLoader.getIcon("/actions/reset-to-default.png"));
  }

  @Override
  public void update(AnActionEvent e) {
    PropertyTable table = DesignerToolWindowManager.getInstance(e.getProject()).getPropertyTable();
    setEnabled(table, e.getPresentation());
  }

  @Override
  public void update(PropertyTable table) {
    setEnabled(table, getTemplatePresentation());
  }

  private static void setEnabled(PropertyTable table, Presentation presentation) {
    try {
      Property property = table.getSelectionProperty();
      presentation.setEnabled(property != null && !table.isDefault(property));
    }
    catch (Exception e) {
      presentation.setEnabled(false);
    }
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    PropertyTable table = DesignerToolWindowManager.getInstance(e.getProject()).getPropertyTable();
    table.restoreDefaultValue();
  }
}