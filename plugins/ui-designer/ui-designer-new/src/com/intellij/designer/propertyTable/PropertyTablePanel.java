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
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class PropertyTablePanel extends JPanel {
  private final DefaultActionGroup myActionGroup = new DefaultActionGroup();
  private final ActionToolbar myToolbar;
  private final PropertyTable myPropertyTable = new PropertyTable();

  public PropertyTablePanel() {
    createActions();
    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, myActionGroup, true);

    setLayout(new BorderLayout());
    setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));

    JPanel titlePanel = new JPanel(new BorderLayout());
    JLabel titleLabel = new JLabel(DesignerBundle.message("designer.properties.title"));
    titleLabel.setBorder(IdeBorderFactory.createEmptyBorder(2, 5, 2, 0));
    titlePanel.add(titleLabel, BorderLayout.LINE_START);
    titlePanel.add(myToolbar.getComponent(), BorderLayout.LINE_END);
    add(titlePanel, BorderLayout.PAGE_START);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPropertyTable);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    add(scrollPane, BorderLayout.CENTER);
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
        myPropertyTable.setShowExpert(state);
      }
    });
  }

  public PropertyTable getPropertyTable() {
    return myPropertyTable;
  }
}