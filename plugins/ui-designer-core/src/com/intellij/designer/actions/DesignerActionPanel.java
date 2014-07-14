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

import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class DesignerActionPanel implements DataProvider {
  public static final String TOOLBAR = "DesignerToolbar";

  private final DefaultActionGroup myActionGroup;
  private final DefaultActionGroup myStaticGroup = new DefaultActionGroup();
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();
  private final DefaultActionGroup myPopupGroup = new DefaultActionGroup();
  private final DefaultActionGroup myDynamicPopupGroup = new DefaultActionGroup();

  protected final JComponent myToolbar;
  protected final DesignerEditorPanel myDesigner;
  private final CommonEditActionsProvider myCommonEditActionsProvider;
  private final JComponent myShortcuts;

  public DesignerActionPanel(DesignerEditorPanel designer, JComponent shortcuts) {
    myDesigner = designer;
    myCommonEditActionsProvider = new CommonEditActionsProvider(designer);
    myShortcuts = shortcuts;

    createInplaceEditingAction(myShortcuts).setDesignerPanel(designer);

    myActionGroup = createActionGroup();
    myToolbar = createToolbar();

    ActionManager actionManager = ActionManager.getInstance();
    myPopupGroup.add(actionManager.getAction(IdeActions.ACTION_CUT));
    myPopupGroup.add(actionManager.getAction(IdeActions.ACTION_COPY));
    myPopupGroup.add(actionManager.getAction(IdeActions.ACTION_PASTE));
    myPopupGroup.addSeparator();
    myPopupGroup.add(actionManager.getAction(IdeActions.ACTION_DELETE));
    myPopupGroup.addSeparator();
    myPopupGroup.add(createSelectActionGroup(designer));
    myPopupGroup.addSeparator();
    myPopupGroup.add(myDynamicPopupGroup);

    designer.getSurfaceArea().addSelectionListener(new ComponentSelectionListener() {
      @Override
      public void selectionChanged(EditableArea area) {
        updateSelectionActions(area.getSelection());
      }
    });
  }

  protected DefaultActionGroup createActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(myStaticGroup);
    group.add(myDynamicGroup);
    return group;
  }

  protected JComponent createToolbar() {
    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar(TOOLBAR, myActionGroup, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    JComponent toolbar = actionToolbar.getComponent();
    toolbar.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    toolbar.setVisible(false);

    return toolbar;
  }

  @NotNull
  private ActionGroup createSelectActionGroup(DesignerEditorPanel designer) {
    final DefaultActionGroup group = new DefaultActionGroup("_Select", true);

    AnAction selectParent = new AnAction("Select Parent", "Select Parent", null) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.getToolProvider().processKeyEvent(new KeyEvent(myDesigner.getSurfaceArea().getNativeComponent(),
                                                                  KeyEvent.KEY_PRESSED, 0, 0,
                                                                  KeyEvent.VK_ESCAPE,
                                                                  (char)KeyEvent.VK_ESCAPE),
                                                     myDesigner.getSurfaceArea()
        );
      }
    };
    selectParent.registerCustomShortcutSet(KeyEvent.VK_ESCAPE, 0, null);

    EditableArea area = designer.getSurfaceArea();

    AnAction selectSiblings = new SelectSiblingsAction(area);
    AnAction selectSameType = new SelectSameTypeAction(area);
    AnAction deselectAllAction = new DeselectAllAction(area);

    AnAction selectAllAction = createSelectAllAction(area);
    registerAction(selectAllAction, "$SelectAll");

    group.add(selectParent);
    group.add(selectSiblings);
    group.add(selectSameType);
    group.addSeparator();
    group.add(selectAllAction);
    group.add(deselectAllAction);

    return group;
  }

  public AnAction createSelectAllAction(EditableArea area) {
    return new SelectAllAction(area);
  }

  public static StartInplaceEditing createInplaceEditingAction(JComponent shortcuts) {
    StartInplaceEditing action = new StartInplaceEditing();
    action.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), shortcuts);
    return action;
  }

  public void registerAction(AnAction action, @NonNls String actionId) {
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

  public DefaultActionGroup getPopupGroup() {
    return myPopupGroup;
  }

  protected DefaultActionGroup getDynamicActionGroup() {
    return myDynamicGroup;
  }

  protected JComponent getShortcuts() {
    return myShortcuts;
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
    boolean oldVisible = isVisible(myDynamicGroup);

    if (myDynamicGroup.getChildrenCount() > 0) {
      for (AnAction action : myDynamicGroup.getChildActionsOrStubs()) {
        action.unregisterCustomShortcutSet(myShortcuts);
      }
      myDynamicGroup.removeAll();
    }

    addSelectionActions(selection, myDynamicGroup);

    if (oldVisible || isVisible(myDynamicGroup)) {
      update();
    }
  }

  protected void addSelectionActions(List<RadComponent> selection, DefaultActionGroup group) {
    for (RadComponent parent : RadComponent.getParents(selection)) {
      parent.getLayout().addSelectionActions(myDesigner, group, myShortcuts, selection);
    }
    for (RadComponent component : selection) {
      component.addSelectionActions(myDesigner, group, myShortcuts, selection);
    }
  }

  public ActionGroup getPopupActions(EditableArea area) {
    if (myDynamicPopupGroup.getChildrenCount() > 0) {
      myDynamicPopupGroup.removeAll();
    }

    WrapInAction.fill(myDesigner, myDynamicPopupGroup, area);
    MorphingAction.fill(myDesigner, myDynamicPopupGroup, area);

    return myPopupGroup;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      JTable table = DesignerToolWindowManager.getInstance(myDesigner).getPropertyTable();
      Component focusOwner = IdeFocusManager.getInstance(myDesigner.getProject()).getFocusOwner();
      if (!UIUtil.isAncestor(table, focusOwner)) {
        return myCommonEditActionsProvider;
      }
    }
    return null;
  }
}