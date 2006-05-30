/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.actions.SurroundAction;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFix;

import javax.swing.*;
import java.util.Collections;

/**
 * @author yole
 */
public class NoScrollPaneInspection extends BaseFormInspection {
  public NoScrollPaneInspection() {
    super("NoScrollPane");
  }

  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.no.scroll.pane");
  }

  protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
    if (FormInspectionUtil.isComponentClass(module, component, Scrollable.class) &&
        !FormInspectionUtil.isComponentClass(module, component, JTextField.class) &&
        !FormInspectionUtil.isComponentClass(module, component.getParentContainer(), JScrollPane.class)) {
      collector.addError(getID(), null, UIDesignerBundle.message("inspection.no.scroll.pane"),
                         new EditorQuickFixProvider() {
                           public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                             return new MyQuickFix(editor, component);
                           }
                         });

    }
  }

  private static class MyQuickFix extends QuickFix {
    public MyQuickFix(final GuiEditor editor, RadComponent component) {
      super(editor, UIDesignerBundle.message("inspection.no.scroll.pane.quickfix"), component);
    }

    public void run() {
      new SurroundAction(JScrollPane.class.getName()).actionPerformed(myEditor,
                                                                      Collections.singletonList(myComponent),
                                                                      null);
    }
  }
}
