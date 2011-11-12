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

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.quickFixes.ChangeFieldTypeFix;
import com.intellij.uiDesigner.radComponents.RadAtomicComponent;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class MorphAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.MorphAction");

  private final ComponentItem myLastMorphComponent = null;

  public MorphAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    Processor<ComponentItem> processor = new Processor<ComponentItem>() {
      public boolean process(final ComponentItem selectedValue) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            Runnable runnable = new Runnable() {
              public void run() {
                for(RadComponent c: selection) {
                  if (!morphComponent(editor, c, selectedValue)) break;
                }
                editor.refreshAndSave(true);
              }
            };
            CommandProcessor.getInstance().executeCommand(editor.getProject(), runnable, UIDesignerBundle.message("morph.component.command"), null);
            editor.getGlassLayer().requestFocus();
          }
        });
        return true;
      }
    };

    PaletteListPopupStep step = new PaletteListPopupStep(editor, myLastMorphComponent, processor,
                                                         UIDesignerBundle.message("morph.component.title"));
    step.hideNonAtomic();
    if (selection.size() == 1) {
      step.hideComponentClass(selection.get(0).getComponentClassName());
    }
    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
    FormEditingUtil.showPopupUnderComponent(listPopup, selection.get(0));
  }

  private static boolean morphComponent(final GuiEditor editor, final RadComponent oldComponent, ComponentItem targetItem) {
    targetItem = InsertComponentProcessor.replaceAnyComponentItem(editor, targetItem, "Morph to Non-Palette Component");
    if (targetItem == null) {
      return false;
    }
    final RadComponent newComponent = InsertComponentProcessor.createInsertedComponent(editor, targetItem);
    if (newComponent == null) return false;
    newComponent.setBinding(oldComponent.getBinding());
    newComponent.setCustomLayoutConstraints(oldComponent.getCustomLayoutConstraints());
    newComponent.getConstraints().restore(oldComponent.getConstraints());

    updateBoundFieldType(editor, oldComponent, targetItem);

    final IProperty[] oldProperties = oldComponent.getModifiedProperties();
    final Palette palette = Palette.getInstance(editor.getProject());
    for(IProperty prop: oldProperties) {
      IntrospectedProperty newProp = palette.getIntrospectedProperty(newComponent, prop.getName());
      if (newProp == null || !prop.getClass().equals(newProp.getClass())) continue;
      Object oldValue = prop.getPropertyValue(oldComponent);
      try {
        //noinspection unchecked
        newProp.setValue(newComponent, oldValue);
      }
      catch (Exception e) {
        // ignore
      }
    }

    retargetComponentProperties(editor, oldComponent, newComponent);
    final RadContainer parent = oldComponent.getParent();
    int index = parent.indexOfComponent(oldComponent);
    parent.removeComponent(oldComponent);
    parent.addComponent(newComponent, index);
    newComponent.setSelected(true);

    if (oldComponent.isDefaultBinding()) {
      final String text = FormInspectionUtil.getText(newComponent.getModule(), newComponent);
      if (text != null) {
        String binding = BindingProperty.suggestBindingFromText(newComponent, text);
        if (binding != null) {
          new BindingProperty(newComponent.getProject()).setValueEx(newComponent, binding);
        }
      }
      newComponent.setDefaultBinding(true);
    }
    return true;
  }

  private static void updateBoundFieldType(final GuiEditor editor, final RadComponent oldComponent, final ComponentItem targetItem) {
    PsiField oldBoundField = BindingProperty.findBoundField(editor.getRootContainer(), oldComponent.getBinding());
    if (oldBoundField != null) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(editor.getProject()).getElementFactory();
      try {
        PsiType componentType = factory.createTypeFromText(targetItem.getClassName().replace('$', '.'), null);
        new ChangeFieldTypeFix(editor, oldBoundField, componentType).run();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static void retargetComponentProperties(final GuiEditor editor, final RadComponent c, final RadComponent newComponent) {
    FormEditingUtil.iterate(editor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        RadComponent rc = (RadComponent) component;
        for(IProperty p: component.getModifiedProperties()) {
          if (p instanceof IntroComponentProperty) {
            IntroComponentProperty icp = (IntroComponentProperty) p;
            final String value = icp.getValue(rc);
            if (value.equals(c.getId())) {
              try {
                icp.setValue((RadComponent)component, newComponent.getId());
              }
              catch (Exception e) {
                // ignore
              }
            }
          }
        }
        return true;
      }
    });
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    if (selection.size() == 0) {
      e.getPresentation().setEnabled(false);
      return;
    }
    for(RadComponent c: selection) {
      if (!(c instanceof RadAtomicComponent)) {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
  }
}
