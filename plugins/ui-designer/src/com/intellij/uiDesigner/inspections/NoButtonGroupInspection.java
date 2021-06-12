// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.actions.GroupButtonsAction;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;


public class NoButtonGroupInspection extends BaseFormInspection {
  private static final Logger LOG = Logger.getInstance(NoButtonGroupInspection.class);

  public NoButtonGroupInspection() {
    super("NoButtonGroup");
  }

  @Override
  protected void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector) {
    if (FormInspectionUtil.isComponentClass(module, component, JRadioButton.class)) {
      final IRootContainer root = FormEditingUtil.getRoot(component);
      if (root == null) return;
      if (root.getButtonGroupName(component) == null) {
        EditorQuickFixProvider quickFixProvider = null;
        IContainer parent = component.getParentContainer();
        for(int i=0; i<parent.getComponentCount(); i++) {
          IComponent child = parent.getComponent(i);
          if (child != component &&
              FormInspectionUtil.isComponentClass(module, child, JRadioButton.class)) {
            final GridConstraints c1 = component.getConstraints();
            final GridConstraints c2 = child.getConstraints();
            if (areCellsAdjacent(parent, c1, c2)) {
              final String groupName = root.getButtonGroupName(child);
              if (groupName == null) {
                quickFixProvider = (editor, component1) -> new CreateGroupQuickFix(editor, component1, c1.getColumn() == c2.getColumn());
                break;
              }
              else {
                quickFixProvider = (editor, component12) -> new AddToGroupQuickFix(editor, component12, groupName);
              }
            }
          }
        }
        collector.addError(getID(), component, null, UIDesignerBundle.message("inspection.no.button.group.error"), quickFixProvider);
      }
    }
  }

  private static boolean areCellsAdjacent(final IContainer parent, final GridConstraints c1, final GridConstraints c2) {
    if (parent instanceof RadContainer) {
      final RadContainer container = (RadContainer)parent;
      if (!container.getLayoutManager().isGrid()) return false;
      if (c1.getRow() == c2.getRow()) {
        return FormEditingUtil.prevCol(container, c1.getColumn()) == c2.getColumn() ||
               FormEditingUtil.nextCol(container, c1.getColumn()) == c2.getColumn();
      }
      if (c1.getColumn() == c2.getColumn()) {
        return FormEditingUtil.prevRow(container, c1.getRow()) == c2.getRow() ||
               FormEditingUtil.nextRow(container, c1.getRow()) == c2.getRow();
      }
    }
    return c1.getRow() == c2.getRow() && Math.abs(c1.getColumn() - c2.getColumn()) == 1 ||
           c1.getColumn() == c2.getColumn() && Math.abs(c1.getRow() - c2.getRow()) == 1;
  }

  private static class CreateGroupQuickFix extends QuickFix {
    private final boolean myVerticalGroup;

    CreateGroupQuickFix(final GuiEditor editor, final RadComponent component, boolean verticalGroup) {
      super(editor, UIDesignerBundle.message("inspection.no.button.group.quickfix.create"), component);
      myVerticalGroup = verticalGroup;
    }

    @Override
    public void run() {
      RadContainer parent = myComponent.getParent();
      ArrayList<RadComponent> buttonsToGroup = new ArrayList<>();
      for(RadComponent component: parent.getComponents()) {
        if (FormInspectionUtil.isComponentClass(myComponent.getModule(), component, JRadioButton.class)) {
          if (component.getConstraints().getCell(!myVerticalGroup) == myComponent.getConstraints().getCell(!myVerticalGroup))
            buttonsToGroup.add(component);
        }
      }
      buttonsToGroup.sort((o1, o2) -> {
        if (myVerticalGroup) {
          return o1.getConstraints().getRow() - o2.getConstraints().getRow();
        }
        return o1.getConstraints().getColumn() - o2.getConstraints().getColumn();
      });

      // ensure that selected radio buttons are in adjacent cells, and exclude from grouping
      // buttons separated by empty cells or other controls
      int index=buttonsToGroup.indexOf(myComponent);
      LOG.assertTrue(index >= 0);
      int expectCell = myComponent.getConstraints().getCell(myVerticalGroup);
      for(int i=index-1; i >= 0; i--) {
        expectCell = FormEditingUtil.adjustForGap(parent, expectCell-1, myVerticalGroup, -1);
        if (buttonsToGroup.get(i).getConstraints().getCell(myVerticalGroup) != expectCell) {
          buttonsToGroup.subList(0, i + 1).clear();
          break;
        }
      }
      expectCell = myComponent.getConstraints().getCell(myVerticalGroup);
      for(int i=index+1; i<buttonsToGroup.size(); i++) {
        expectCell = FormEditingUtil.adjustForGap(parent, expectCell+1, myVerticalGroup, 1);
        if (buttonsToGroup.get(i).getConstraints().getCell(myVerticalGroup) != expectCell) {
          buttonsToGroup.subList(i, buttonsToGroup.size()).clear();
          break;
        }
      }

      LOG.assertTrue(buttonsToGroup.size() > 1);
      GroupButtonsAction.groupButtons(myEditor, buttonsToGroup);
    }
  }

  private static class AddToGroupQuickFix extends QuickFix {
    private final String myGroupName;

    AddToGroupQuickFix(final GuiEditor editor, final RadComponent component, final String groupName) {
      super(editor, UIDesignerBundle.message("inspection.no.button.group.quickfix.add", groupName), component);
      myGroupName = groupName;
    }

    @Override
    public void run() {
      RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(myComponent);
      if (root == null) return;
      for(RadButtonGroup group: root.getButtonGroups()) {
        if (group.getName().equals(myGroupName)) {
          root.setGroupForComponent(myComponent, group);
          break;
        }
      }
      myEditor.refreshAndSave(true);
    }
  }
}
