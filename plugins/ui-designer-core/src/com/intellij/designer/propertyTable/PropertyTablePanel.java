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
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.propertyTable.actions.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public final class PropertyTablePanel extends JPanel implements ListSelectionListener {
  private static final String BUTTON_KEY = "SWING_BUTTON_KEY";

  private final RadPropertyTable myPropertyTable;
  private final AnAction[] myActions;
  private final JPanel myTabPanel;
  private final JPanel myActionPanel;

  private PropertyTableTab[] myTabs;
  private PropertyTableTab myCurrentTab;
  private TablePanelActionPolicy myActionPolicy;
  private final JLabel myTitleLabel;

  public PropertyTablePanel(final Project project) {
    myPropertyTable = new RadPropertyTable(project) {
      @Override
      protected void updateEditActions() {
        updateActions();
      }
    };

    setLayout(new GridBagLayout());

    int gridX = 0;

    myTitleLabel = new JLabel(DesignerBundle.message("designer.properties.title"));
    myTitleLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    add(myTitleLabel,
        new GridBagConstraints(gridX++, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               new Insets(2, 5, 2, 10), 0, 0)
    );

    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ShowJavadoc showJavadoc = new ShowJavadoc(myPropertyTable);
    showJavadoc.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_QUICK_JAVADOC).getShortcutSet(), myPropertyTable);
    actionGroup.add(showJavadoc);

    actionGroup.addSeparator();

    RestoreDefault restoreDefault = new RestoreDefault(myPropertyTable);
    restoreDefault.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_DELETE).getShortcutSet(), myPropertyTable);
    actionGroup.add(restoreDefault);

    actionGroup.add(new ShowExpert(myPropertyTable));

    myTabPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    add(myTabPanel,
        new GridBagConstraints(gridX++, 0, 1, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                               new Insets(2, 0, 2, 0), 0, 0)
    );

    myActionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    add(myActionPanel,
        new GridBagConstraints(gridX++, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                               new Insets(2, 0, 2, 2), 0, 0)
    );

    myActions = actionGroup.getChildren(null);
    for (AnAction action : myActions) {
      if (action instanceof Separator) {
        continue;
      }

      Presentation presentation = action.getTemplatePresentation();
      ActionButton button = new ActionButton(action, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      myActionPanel.add(button);
      presentation.putClientProperty(BUTTON_KEY, button);
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
    add(scrollPane, new GridBagConstraints(0, 1, gridX, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           new Insets(0, 0, 0, 0), 0, 0));

    myPropertyTable.setPropertyTablePanel(this);

    addMouseListener(new MouseAdapter() {
      public void mouseReleased(final MouseEvent e) {
        IdeFocusManager.getInstance(project).requestFocus(myPropertyTable, true);
      }
    });
  }

  public void setArea(@Nullable DesignerEditorPanel designer, @Nullable EditableArea area) {
    PropertyTableTab[] tabs = designer == null ? null : designer.getPropertyTableTabs();
    if (!Comparing.equal(myTabs, tabs)) {
      myTabs = tabs;
      myTabPanel.removeAll();

      if (tabs != null && tabs.length > 1) {
        if (!ArrayUtil.contains(myCurrentTab, tabs)) {
          myCurrentTab = tabs[0];
        }

        for (PropertyTableTab tab : tabs) {
          myTabPanel.add(new TableTabAction(this, tab).getButton());
        }
      }
      else {
        myCurrentTab = null;
      }

      myTitleLabel.setVisible(myCurrentTab == null);
      myTabPanel.revalidate();
    }

    TablePanelActionPolicy policy = designer == null ? TablePanelActionPolicy.EMPTY : designer.getTablePanelActionPolicy();
    if (!Comparing.equal(myActionPolicy, policy)) {
      myActionPolicy = policy;

      for (AnAction action : myActions) {
        if (action instanceof Separator) {
          continue;
        }

        boolean visible = policy.showAction(action);

        Presentation presentation = action.getTemplatePresentation();
        presentation.setVisible(visible);

        JComponent button = (JComponent)presentation.getClientProperty(BUTTON_KEY);
        if (button != null) {
          button.setVisible(visible);
        }
      }

      myActionPanel.revalidate();
    }

    myPropertyTable.setArea(designer, area);
  }

  public RadPropertyTable getPropertyTable() {
    return myPropertyTable;
  }

  @Nullable
  public PropertyTableTab getCurrentTab() {
    return myCurrentTab;
  }

  public void setCurrentTab(@NotNull PropertyTableTab currentTab) {
    myCurrentTab = currentTab;

    for (Component component : myTabPanel.getComponents()) {
      ActionButton button = (ActionButton)component;
      TableTabAction action = (TableTabAction)button.getAction();
      action.updateState();
    }

    myPropertyTable.update();
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