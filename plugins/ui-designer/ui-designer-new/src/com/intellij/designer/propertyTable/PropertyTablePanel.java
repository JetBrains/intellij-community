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
import com.intellij.designer.propertyTable.actions.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class PropertyTablePanel extends JPanel implements ListSelectionListener {
  private final RadPropertyTable myPropertyTable;
  private final AnAction[] myActions;

  public PropertyTablePanel(Project project) {
    myPropertyTable = new RadPropertyTable(project);

    setLayout(new GridBagLayout());
    setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));

    JLabel titleLabel = new JLabel(DesignerBundle.message("designer.properties.title"));
    titleLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    add(titleLabel,
        new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               new Insets(2, 5, 2, 0), 0, 0));

    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ShowJavadoc showJavadoc = new ShowJavadoc(myPropertyTable);
    showJavadoc.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_QUICK_JAVADOC).getShortcutSet(), myPropertyTable);
    actionGroup.add(showJavadoc);

    actionGroup.addSeparator();

    RestoreDefault restoreDefault = new RestoreDefault(myPropertyTable);
    restoreDefault.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_DELETE).getShortcutSet(), null);
    actionGroup.add(restoreDefault);

    actionGroup.add(new ShowExpert(myPropertyTable));

    myActions = actionGroup.getChildren(null);
    for (int i = 0; i < myActions.length; i++) {
      AnAction action = myActions[i];
      if (!(action instanceof Separator)) {
        add(new ActionButton(action, action.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE),
            new GridBagConstraints(i + 1, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                   new Insets(2, 0, 2, i == myActions.length - 1 ? 2 : 0), 0, 0));
      }
    }

    actionGroup.add(new ShowColumns(myPropertyTable));

    PopupHandler.installPopupHandler(myPropertyTable, actionGroup,
                                     ActionPlaces.GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP,
                                     actionManager);

    myPropertyTable.getSelectionModel().addListSelectionListener(this);
    valueChanged(null);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPropertyTable);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    myPropertyTable.initQuickFixManager(scrollPane.getViewport());
    add(scrollPane, new GridBagConstraints(0, 1, myActions.length + 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           new Insets(0, 0, 0, 0), 0, 0));

    myPropertyTable.setPropertyTablePanel(this);
  }

  public RadPropertyTable getPropertyTable() {
    return myPropertyTable;
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    updateActions();
  }

  public void updateActions() {
    for (AnAction action : myActions) {
      if (action instanceof IPropertyTableAction) {
        ((IPropertyTableAction)action).update();
      }
    }
  }
}