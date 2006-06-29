/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.IRootContainer;
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
    return project.getComponent(CustomCreateProperty.class);
  }

  private BooleanRenderer myRenderer = new BooleanRenderer();

  private BooleanEditor myEditor = new BooleanEditor() {
    @Override
    public JComponent getComponent(final RadComponent component, final Boolean value, final boolean inplace) {
      JComponent result = super.getComponent(component, value, inplace);
      result.setEnabled(!component.isCustomCreateRequired());
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
      final PsiNameHelper nameHelper = PsiManager.getInstance(component.getProject()).getNameHelper();
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
    final Ref<SmartPsiElementPointer> refMethod = new Ref<SmartPsiElementPointer>();
    CommandProcessor.getInstance().executeCommand(
      aClass.getProject(),
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiElementFactory factory = aClass.getManager().getElementFactory();
              PsiMethod method;
              try {
                method = factory.createMethodFromText("private void " +
                                                      AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME +
                                                      "() { \n // TODO: place custom component creation code here \n }",
                                                      aClass);
                final PsiMethod psiMethod = (PsiMethod)aClass.add(method);
                refMethod.set(SmartPointerManager.getInstance(aClass.getProject()).createSmartPsiElementPointer(psiMethod));
                CodeStyleManager.getInstance(aClass.getProject()).reformat(psiMethod);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          });
        }
      }, null, null
    );

    if (!refMethod.isNull()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          final PsiMethod element = (PsiMethod) refMethod.get().getElement();
          if (element != null) {
            final PsiCodeBlock body = element.getBody();
            assert body != null;
            final PsiComment comment = PsiTreeUtil.getChildOfType(body, PsiComment.class);
            if (comment != null) {
              new OpenFileDescriptor(comment.getProject(), comment.getContainingFile().getVirtualFile(),
                                     comment.getTextOffset()).navigate(true);
            }
          }
        }
      });
    }
  }

}
