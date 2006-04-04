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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.FormEditingUtil;
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
      result.setEnabled(component.getBinding() != null);
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

  protected void setValueImpl(final RadComponent component, final Boolean value) throws Exception {
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
    final Ref<PsiMethod> refMethod = new Ref<PsiMethod>();
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
                refMethod.set((PsiMethod) aClass.add(method));
                CodeStyleManager.getInstance(aClass.getProject()).reformat(refMethod.get());
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
          final PsiCodeBlock body = refMethod.get().getBody();
          assert body != null;
          final PsiComment comment = PsiTreeUtil.getChildOfType(body, PsiComment.class);
          if (comment != null) {
            new OpenFileDescriptor(comment.getProject(), comment.getContainingFile().getVirtualFile(),
                                   comment.getTextOffset()).navigate(true);
          }
        }
      });
    }
  }
}
