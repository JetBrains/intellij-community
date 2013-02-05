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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;

/**
 * @author Alexander Lobas
 */
public class ShowExpert extends ToggleAction {

  private final RadPropertyTable myTable;

  public ShowExpert(RadPropertyTable table) {
    myTable = table;

    Presentation presentation = getTemplatePresentation();
    String text = DesignerBundle.message("designer.properties.show.expert");
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(AllIcons.General.Filter);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    if (ActionPlaces.GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP.equals(e.getPlace())) {
      presentation.setIcon(null);
    }
    else {
      presentation.setIcon(AllIcons.General.Filter);
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myTable.isShowExpertProperties();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myTable.showExpert(state);
    if (ActionPlaces.GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP.equals(e.getPlace())) {
      getTemplatePresentation().putClientProperty(SELECTED_PROPERTY, state);
    }
  }
}
