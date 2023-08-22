// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.actions.SurroundAction;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;


public class NoScrollPaneInspection extends BaseFormInspection {
  public NoScrollPaneInspection() {
    super("NoScrollPane");
  }

  @Override
  protected void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector) {
    if (FormInspectionUtil.isComponentClass(module, component, Scrollable.class) &&
        !FormInspectionUtil.isComponentClass(module, component, JTextField.class) &&
        !FormInspectionUtil.isComponentClass(module, component.getParentContainer(), JScrollPane.class)) {
      collector.addError(getID(), component, null, UIDesignerBundle.message("inspection.no.scroll.pane"),
                         MyQuickFix::new);

    }
  }

  private static class MyQuickFix extends QuickFix {
    MyQuickFix(final GuiEditor editor, RadComponent component) {
      super(editor, UIDesignerBundle.message("inspection.no.scroll.pane.quickfix"), component);
    }

    @Override
    public void run() {
      String scrollPane = JScrollPane.class.getName();
      ComponentItem item = Palette.getInstance(myEditor.getProject()).getItem(scrollPane);

      SurroundAction action = new SurroundAction(item == null ? JBScrollPane.class.getName() : scrollPane);

      ArrayList<RadComponent> targetList = new ArrayList<>(Collections.singletonList(myComponent));
      action.actionPerformed(myEditor, targetList, null);
    }
  }
}
