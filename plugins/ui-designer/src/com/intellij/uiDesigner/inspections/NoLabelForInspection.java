/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * @author yole
 */
public class NoLabelForInspection extends BaseFormInspection {
  public NoLabelForInspection() {
    super("NoLabelFor");
  }

  @NotNull
  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.no.label.for");
  }

  protected void checkComponentProperties(final Module module, final IComponent component, FormErrorCollector collector) {
    ComponentItem item = Palette.getInstance(module.getProject()).getItem(component.getComponentClassName());
    if (item != null && item.isCanAttachLabel()) {
      IComponent root = component;
      while(root.getParentContainer() != null) {
        root = root.getParentContainer();
      }
      final Ref<Boolean> found = new Ref<>(Boolean.FALSE);
      final Ref<RadComponent> candidateLabel = new Ref<>();
      final List<RadComponent> allLabels = new ArrayList<>();
      FormEditingUtil.iterate(root, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent c2) {
          if (FormInspectionUtil.isComponentClass(module, c2, JLabel.class)) {
            IProperty prop = FormInspectionUtil.findProperty(c2, SwingProperties.LABEL_FOR);
            if (prop != null && component.getId().equals(prop.getPropertyValue(c2))) {
              found.set(Boolean.TRUE);
              return false;
            }
            else if (component instanceof RadComponent &&
                     (prop == null || StringUtil.isEmpty((String)prop.getPropertyValue(c2)))) {
              RadComponent radComponent = (RadComponent) component;
              final RadComponent radComponent2 = ((RadComponent)c2);
              allLabels.add(radComponent2);
              if (radComponent.getParent() == radComponent2.getParent() && radComponent.getParent().getLayoutManager().isGrid()) {
                GridConstraints gc1 = radComponent.getConstraints();
                GridConstraints gc2 = radComponent2.getConstraints();
                int nextColumn = FormEditingUtil.nextCol(radComponent.getParent(), gc2.getColumn());
                int nextRow = FormEditingUtil.nextRow(radComponent.getParent(), gc2.getRow());
                if ((gc1.getRow() == gc2.getRow() && nextColumn == gc1.getColumn()) ||
                    (gc1.getColumn() == gc2.getColumn() && nextRow == gc1.getRow())) {
                  candidateLabel.set(radComponent2);
                }
              }
            }
          }
          return true;
        }
      });
      if (!found.get().booleanValue()) {
        if (!candidateLabel.isNull()) {
          allLabels.clear();
          allLabels.add(candidateLabel.get());
        }
        EditorQuickFixProvider[] quickFixProviders = new EditorQuickFixProvider[allLabels.size()];
        for (int i = 0; i < quickFixProviders.length; i++) {
          final RadComponent label = allLabels.get(i);
          quickFixProviders[i] = new EditorQuickFixProvider() {
            public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
              return new MyQuickFix(editor, component, label);
            }
          };
        }
        collector.addError(getID(), component, null, UIDesignerBundle.message("inspection.no.label.for.error"), quickFixProviders);
      }
    }
  }

  private static class MyQuickFix extends QuickFix {
    private final RadComponent myLabel;

    public MyQuickFix(final GuiEditor editor, RadComponent component, RadComponent label) {
      super(editor, UIDesignerBundle.message("inspection.no.label.for.quickfix",
                                             label.getComponentTitle()), component);
      myLabel = label;
    }

    public void run() {
      if (!myEditor.ensureEditable()) {
        return;
      }
      Runnable runnable = () -> {
        final Palette palette = Palette.getInstance(myEditor.getProject());
        IntrospectedProperty[] props = palette.getIntrospectedProperties(myLabel);
        boolean modified = false;
        for(IntrospectedProperty prop: props) {
          if (prop.getName().equals(SwingProperties.LABEL_FOR) && prop instanceof IntroComponentProperty) {
            IntroComponentProperty icp = (IntroComponentProperty) prop;
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
