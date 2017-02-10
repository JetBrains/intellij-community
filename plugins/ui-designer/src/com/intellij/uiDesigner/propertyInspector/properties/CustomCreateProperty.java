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

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class CustomCreateProperty extends Property<RadComponent, Boolean> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.properties.CustomCreateProperty");

  public static CustomCreateProperty getInstance(Project project) {
    return ServiceManager.getService(project, CustomCreateProperty.class);
  }

  private final BooleanRenderer myRenderer = new BooleanRenderer();

  private final BooleanEditor myEditor = new BooleanEditor() {
    @Override
    public JComponent getComponent(final RadComponent component, final Boolean value, final InplaceContext inplaceContext) {
      JCheckBox result = (JCheckBox) super.getComponent(component, value, inplaceContext);
      final boolean customCreateRequired = component.isCustomCreateRequired();
      if (customCreateRequired) {
        result.setEnabled(false);
        result.setSelected(true);
      }
      else {
        result.setEnabled(true);
      }
      return result;
    }
  };

  public CustomCreateProperty() {
    super(null, "Custom Create");
  }

  public Boolean getValue(final RadComponent component) {
    return component.isCustomCreate();
  }

  @NotNull
  public PropertyRenderer<Boolean> getRenderer() {
   return myRenderer;
  }

  public PropertyEditor<Boolean> getEditor() {
    return myEditor;
  }

  @Override
  public boolean appliesToSelection(final List<RadComponent> selection) {
    if (selection.size() > 1) {
      // possible "enabled" state may be different
      for(RadComponent c: selection) {
        if (c.isCustomCreateRequired()) {
          return false;
        }
      }
    }
    return true;
  }

  protected void setValueImpl(final RadComponent component, final Boolean value) throws Exception {
    if (value.booleanValue() && component.getBinding() == null) {
      String initialBinding = BindingProperty.getDefaultBinding(component);
      String binding = Messages.showInputDialog(
        component.getProject(),
        UIDesignerBundle.message("custom.create.field.name.prompt"),
        UIDesignerBundle.message("custom.create.title"), Messages.getQuestionIcon(),
        initialBinding, new IdentifierValidator(component.getProject()));
      if (binding == null) {
        return;
      }
      try {
        new BindingProperty(component.getProject()).setValue(component, binding);
      }
      catch (Exception e1) {
        LOG.error(e1);
      }
    }
    component.setCustomCreate(value.booleanValue());
    if (value.booleanValue()) {
      final IRootContainer root = FormEditingUtil.getRoot(component);
      if (root.getClassToBind() != null && Utils.getCustomCreateComponentCount(root) == 1) {
        final PsiClass aClass = FormEditingUtil.findClassToBind(component.getModule(), root.getClassToBind());
        if (aClass != null && FormEditingUtil.findCreateComponentsMethod(aClass) == null) {
          generateCreateComponentsMethod(aClass);
        }
      }
    }
  }

  public static void generateCreateComponentsMethod(final PsiClass aClass) {
    final PsiFile psiFile = aClass.getContainingFile();
    if (psiFile == null) return;
    final VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;

    final Ref<SmartPsiElementPointer> refMethod = new Ref<>();
    CommandProcessor.getInstance().executeCommand(
      aClass.getProject(),
      () -> ApplicationManager.getApplication().runWriteAction(() -> {
        PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
        try {
          PsiMethod method = factory.createMethodFromText("private void " +
                                                          AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME +
                                                          "() { \n // TODO: place custom component creation code here \n }", aClass);
          final PsiMethod psiMethod = (PsiMethod)aClass.add(method);
          refMethod.set(SmartPointerManager.getInstance(aClass.getProject()).createSmartPsiElementPointer(psiMethod));
          CodeStyleManager.getInstance(aClass.getProject()).reformat(psiMethod);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }), null, null
    );

    if (!refMethod.isNull()) {
      SwingUtilities.invokeLater(() -> {
        final PsiMethod element = (PsiMethod) refMethod.get().getElement();
        if (element != null) {
          final PsiCodeBlock body = element.getBody();
          assert body != null;
          final PsiComment comment = PsiTreeUtil.getChildOfType(body, PsiComment.class);
          if (comment != null) {
            new OpenFileDescriptor(comment.getProject(), vFile,
                                   comment.getTextOffset()).navigate(true);
          }
        }
      });
    }
  }

}
