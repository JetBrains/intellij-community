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
package com.intellij.designer.actions;

import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class DesignerActionPanel implements DataProvider {
  public static final String TOOLBAR = "DesignerToolbar";
  public static final String POPUP = "DesignerPopup";

  private final DefaultActionGroup myActionGroup = new DefaultActionGroup();
  private final DefaultActionGroup myStaticGroup = new DefaultActionGroup();
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();
  private JComponent myToolbar;
  private final CommonEditActionsProvider myCommonEditActionsProvider;
  private final JComponent myShortcuts;

  public DesignerActionPanel(DesignerEditorPanel designer, JComponent shortcuts) {
    myCommonEditActionsProvider = new CommonEditActionsProvider(designer);
    myShortcuts = shortcuts;

    myActionGroup.add(myStaticGroup);
    myActionGroup.add(myDynamicGroup);

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR, myActionGroup, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    myToolbar = actionToolbar.getComponent();
    myToolbar.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    myToolbar.setVisible(false);

    registerAction(new SelectAllAction(designer.getSurfaceArea()), "$SelectAll");

    designer.getSurfaceArea().addSelectionListener(new ComponentSelectionListener() {
      @Override
      public void selectionChanged(EditableArea area) {
        updateSelectionActions(area.getSelection());
      }
    });

    // TODO: support popup
  }

  private void registerAction(AnAction action, @NonNls String actionId) {
    action.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(actionId).getShortcutSet(),
      myShortcuts
    );
  }

  public JComponent getToolbarComponent() {
    return myToolbar;
  }

  public DefaultActionGroup getActionGroup() {
    return myStaticGroup;
  }

  public void update() {
    boolean oldVisible = myToolbar.isVisible();
    boolean newVisible = isVisible(myActionGroup);
    myToolbar.setVisible(newVisible);
    if (oldVisible && newVisible) {
      ((JComponent)myToolbar.getParent()).revalidate();
    }
  }

  private static boolean isVisible(DefaultActionGroup group) {
    if (group.getChildrenCount() == 0) {
      return false;
    }

    for (AnAction action : group.getChildren(null)) {
      if (action instanceof DefaultActionGroup) {
        if (isVisible((DefaultActionGroup)action)) {
          return true;
        }
      }
      else {
        return true;
      }
    }
    return false;
  }

  private void updateSelectionActions(List<RadComponent> selection) {
    boolean update = isVisible(myDynamicGroup);

    for (AnAction action : myDynamicGroup.getChildActionsOrStubs()) {
      action.unregisterCustomShortcutSet(myShortcuts);
    }
    myDynamicGroup.removeAll();

    Set<RadComponent> parents = new HashSet<RadComponent>();
    for (RadComponent component : selection) {
      RadComponent parent = component.getParent();
      if (parent != null) {
        parents.add(parent);
      }
    }

    for (RadComponent parent : parents) {
      parent.getLayout().addSelectionActions(myDynamicGroup, myShortcuts, selection);
    }
    for (RadComponent component : selection) {
      component.addSelectionActions(myDynamicGroup, myShortcuts, selection);
    }
    update |= isVisible(myDynamicGroup);

    if (update) {
      update();
    }
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myCommonEditActionsProvider;
    }
    return null;
  }
}