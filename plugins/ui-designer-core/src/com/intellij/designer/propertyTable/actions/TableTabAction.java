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
package com.intellij.designer.propertyTable.actions;

import com.intellij.designer.propertyTable.PropertyTablePanel;
import com.intellij.designer.propertyTable.PropertyTableTab;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;

/**
 * @author Alexander Lobas
 */
public class TableTabAction extends ToggleAction implements DumbAware {
  private final PropertyTablePanel myPanel;
  private final PropertyTableTab myTab;
  private final ActionButton myButton;

  public TableTabAction(PropertyTablePanel panel, PropertyTableTab tab) {
    myPanel = panel;
    myTab = tab;

    Presentation presentation = getTemplatePresentation();
    String text = tab.getDescription();
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(tab.getIcon());

    myButton = new ActionButton(this, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);

    updateState();
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myTab == myPanel.getCurrentTab();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (state) {
      myPanel.setCurrentTab(myTab);
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          updateState();
        }
      });
    }
  }

  public ActionButton getButton() {
    return myButton;
  }

  public void updateState() {
    getTemplatePresentation().putClientProperty(Toggleable.SELECTED_PROPERTY, Boolean.valueOf(isSelected(null)));
    myButton.repaint();
  }
}