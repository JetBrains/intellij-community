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
package com.intellij.designer.propertyTable;

import com.intellij.designer.DesignerBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class PropertyTablePanel extends JPanel {
  private final DefaultActionGroup myActionGroup = new DefaultActionGroup();
  private final PropertyTable myPropertyTable = new PropertyTable();

  public PropertyTablePanel() {
    setLayout(new GridBagLayout());
    setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));

    add(new JLabel(DesignerBundle.message("designer.properties.title")),
        new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(2, 5, 2, 0), 0, 0));

    createActions();

    AnAction[] actions = myActionGroup.getChildren(null);
    for (int i = 0; i < actions.length; i++) {
      AnAction action = actions[i];
      add(new ActionButton(action, action.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE),
          new GridBagConstraints(i + 1, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                 new Insets(2, 0, 2, i == actions.length - 1 ? 2 : 0), 0, 0));
    }

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPropertyTable);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    add(scrollPane, new GridBagConstraints(0, 1, actions.length + 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           new Insets(0, 0, 0, 0), 0, 0));
  }

  private void createActions() {
    String restore = DesignerBundle.message("designer.properties.restore_default");
    myActionGroup.add(new AnAction(restore, restore, IconLoader.getIcon("/actions/reset-to-default.png")) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myPropertyTable.restoreDefaultValue();
      }
    });

    String expert = DesignerBundle.message("designer.properties.show.expert");
    myActionGroup.add(new ToggleAction(expert, expert, IconLoader.getIcon("/com/intellij/designer/icons/filter.png")) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myPropertyTable.isShowExpert();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myPropertyTable.showExpert(state);
      }
    });
  }

  public PropertyTable getPropertyTable() {
    return myPropertyTable;
  }
}