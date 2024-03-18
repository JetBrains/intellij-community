// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;


public class NoLabelForInspection extends BaseFormInspection {
  public NoLabelForInspection() {
    super("NoLabelFor");
  }

  @Override
  protected void checkComponentProperties(final Module module, final @NotNull IComponent component, FormErrorCollector collector) {
    ComponentItem item = Palette.getInstance(module.getProject()).getItem(component.getComponentClassName());
    if (item != null && item.isCanAttachLabel()) {
      IComponent root = component;
      while(root.getParentContainer() != null) {
        root = root.getParentContainer();
      }
      final Ref<Boolean> found = new Ref<>(Boolean.FALSE);
      final Ref<RadComponent> candidateLabel = new Ref<>();
      final List<RadComponent> allLabels = new ArrayList<>();
      FormEditingUtil.iterate(root, c2 -> {
        if (FormInspectionUtil.isComponentClass(module, c2, JLabel.class)) {
          IProperty prop = FormInspectionUtil.findProperty(c2, SwingProperties.LABEL_FOR);
          if (prop != null && component.getId().equals(prop.getPropertyValue(c2))) {
            found.set(Boolean.TRUE);
            return false;
          }
          else if (component instanceof RadComponent radComponent &&
                   (prop == null || StringUtil.isEmpty((String)prop.getPropertyValue(c2)))) {
            final RadComponent radComponent2 = (RadComponent)c2;
            allLabels.add(radComponent2);
            if (radComponent.getParent() == radComponent2.getParent() && radComponent.getParent().getLayoutManager().isGrid()) {
              GridConstraints gc1 = radComponent.getConstraints();
              GridConstraints gc2 = radComponent2.getConstraints();
              int nextColumn = FormEditingUtil.nextCol(radComponent.getParent(), gc2.getColumn());
              int nextRow = FormEditingUtil.nextRow(radComponent.getParent(), gc2.getRow());
              if (gc1.getRow() == gc2.getRow() && nextColumn == gc1.getColumn() ||
                  gc1.getColumn() == gc2.getColumn() && nextRow == gc1.getRow()) {
                candidateLabel.set(radComponent2);
              }
            }
          }
        }
        return true;
      });
      if (!found.get().booleanValue()) {
        if (!candidateLabel.isNull()) {
          allLabels.clear();
          allLabels.add(candidateLabel.get());
        }
        EditorQuickFixProvider[] quickFixProviders = new EditorQuickFixProvider[allLabels.size()];
        for (int i = 0; i < quickFixProviders.length; i++) {
          final RadComponent label = allLabels.get(i);
          quickFixProviders[i] = (editor, component1) -> new MyQuickFix(editor, component1, label);
        }
        collector.addError(getID(), component, null, UIDesignerBundle.message("inspection.no.label.for.error"), quickFixProviders);
      }
    }
  }

  private static class MyQuickFix extends QuickFix {
    private final RadComponent myLabel;

    MyQuickFix(final GuiEditor editor, RadComponent component, RadComponent label) {
      super(editor, UIDesignerBundle.message("inspection.no.label.for.quickfix",
                                             label.getComponentTitle()), component);
      myLabel = label;
    }

    @Override
    public void run() {
      if (!myEditor.ensureEditable()) {
        return;
      }
      Runnable runnable = () -> {
        final Palette palette = Palette.getInstance(myEditor.getProject());
        IntrospectedProperty[] props = palette.getIntrospectedProperties(myLabel);
        boolean modified = false;
        for(IntrospectedProperty prop: props) {
          if (prop.getName().equals(SwingProperties.LABEL_FOR) && prop instanceof IntroComponentProperty icp) {
            icp.setValueEx(myLabel, myComponent.getId());
            modified = true;
            break;
          }
        }
        if (modified) myEditor.refreshAndSave(false);
      };
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), runnable,
                                                    UIDesignerBundle.message("inspection.no.label.for.command"), null);
    }
  }
}
