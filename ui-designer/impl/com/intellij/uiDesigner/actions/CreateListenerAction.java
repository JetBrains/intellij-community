/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author yole
 */
public class CreateListenerAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.CreateListenerAction");

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    RadComponent component = selection.get(0);
    final DefaultActionGroup actionGroup = prepareActionGroup(component);
    final DataContext context = DataManager.getInstance().getDataContext(component.getDelegee());
    final JBPopupFactory factory = JBPopupFactory.getInstance();
    final ListPopup popup = factory.createActionGroupPopup(UIDesignerBundle.message("create.listener.title"), actionGroup, context,
                                                           JBPopupFactory.ActionSelectionAid.NUMBERING, true);
    popup.showUnderneathOf(component.getDelegee());
  }

  private DefaultActionGroup prepareActionGroup(final RadComponent component) {
    final PsiField boundField = BindingProperty.findBoundField(component, component.getBinding());
    if (boundField != null) {
      final DefaultActionGroup actionGroup = new DefaultActionGroup();
      final EventSetDescriptor[] eventSetDescriptors;
      try {
        BeanInfo beanInfo = Introspector.getBeanInfo(component.getComponentClass());
        eventSetDescriptors = beanInfo.getEventSetDescriptors();
      }
      catch (IntrospectionException e) {
        LOG.error(e);
        return null;
      }
      EventSetDescriptor[] sortedDescriptors = new EventSetDescriptor[eventSetDescriptors.length];
      System.arraycopy(eventSetDescriptors, 0, sortedDescriptors, 0, eventSetDescriptors.length);
      Arrays.sort(sortedDescriptors, new Comparator<EventSetDescriptor>() {
        public int compare(final EventSetDescriptor o1, final EventSetDescriptor o2) {
          return o1.getListenerType().getName().compareTo(o2.getListenerType().getName());
        }
      });
      for(EventSetDescriptor descriptor: sortedDescriptors) {
        actionGroup.add(new MyCreateListenerAction(boundField, descriptor));
      }
      return actionGroup;
    }
    return null;
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(selection.size() == 1 &&
                                   selection.get(0).getBinding() != null &&
                                   FormEditingUtil.getRoot(selection.get(0)).getClassToBind() != null);
  }

  private class MyCreateListenerAction extends AnAction {
    private final PsiClass myClass;
    private final PsiField myField;
    private final EventSetDescriptor myDescriptor;
    @NonNls private static final String LISTENER_SUFFIX = "Listener";
    @NonNls private static final String ADAPTER_SUFFIX = "Adapter";

    public MyCreateListenerAction(final PsiField boundField, EventSetDescriptor descriptor) {
      super(descriptor.getListenerType().getSimpleName());
      myClass = boundField.getContainingClass();
      myField = boundField;
      myDescriptor = descriptor;
    }

    public void actionPerformed(AnActionEvent e) {
      CommandProcessor.getInstance().executeCommand(
        myClass.getProject(),
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                createListener();
              }
            });
          }
        }, UIDesignerBundle.message("create.listener.command"), null
      );
    }

    private void createListener() {
      try {
        PsiMethod constructor = findConstructorToInsert(myClass);
        final Module module = ModuleUtil.findModuleForPsiElement(myClass);
        PsiClass listenerClass = null;
        final String listenerClassName = myDescriptor.getListenerType().getName();
        if (listenerClassName.endsWith(LISTENER_SUFFIX)) {
          String adapterClassName = listenerClassName.substring(0, listenerClassName.length() - LISTENER_SUFFIX.length()) +
                                    ADAPTER_SUFFIX;
          listenerClass = myClass.getManager().findClass(adapterClassName,
                                                         GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
        }
        if (listenerClass == null) {
          listenerClass = myClass.getManager().findClass(listenerClassName,
                                                         GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
        }
        if (listenerClass == null) {
          Messages.showErrorDialog(myClass.getProject(), UIDesignerBundle.message("create.listener.class.not.found"), CommonBundle.getErrorTitle());
          return;
        }

        PsiElementFactory factory = myClass.getManager().getElementFactory();

        @NonNls StringBuilder builder = new StringBuilder(myField.getName());
        builder.append(".");
        builder.append(myDescriptor.getAddListenerMethod().getName());
        builder.append("(new ");
        builder.append(listenerClass.getQualifiedName());
        builder.append("() { } );");

        PsiStatement stmt = factory.createStatementFromText(builder.toString(), constructor);
        final PsiCodeBlock body = constructor.getBody();
        LOG.assertTrue(body != null);
        stmt = (PsiStatement) body.addAfter(stmt, body.getLastBodyElement());
        CodeStyleManager.getInstance(body.getProject()).shortenClassReferences(stmt);
        final Ref<PsiClass> newClassRef = new Ref<PsiClass>();
        stmt.accept(new PsiRecursiveElementVisitor() {
          public void visitClass(PsiClass aClass) {
            newClassRef.set(aClass);
          }
        });
        final PsiClass newClass = newClassRef.get();
        final SmartPsiElementPointer ptr = SmartPointerManager.getInstance(myClass.getProject()).createSmartPsiElementPointer(newClass);
        newClass.navigate(true);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            final PsiClass newClass = (PsiClass) ptr.getElement();
            final Editor editor = (Editor) DataManager.getInstance().getDataContext().getData(DataConstants.EDITOR);
            if (editor != null && newClass != null) {
              CommandProcessor.getInstance().executeCommand(
                myClass.getProject(),
                new Runnable() {
                  public void run() {
                    if (OverrideImplementUtil.getMethodSignaturesToImplement(newClass).length != 0) {
                      OverrideImplementUtil.chooseAndImplementMethods(newClass.getProject(), editor, newClass);
                    }
                    else {
                      OverrideImplementUtil.chooseAndOverrideMethods(newClass.getProject(), editor, newClass);
                    }
                  }
                }, "", null);
            }
          }
        });
      }
      catch (IncorrectOperationException ex) {
        LOG.error(ex);
      }
    }

    private PsiMethod findConstructorToInsert(final PsiClass aClass) throws IncorrectOperationException {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        PsiElementFactory factory = aClass.getManager().getElementFactory();
        PsiMethod newConstructor = factory.createMethodFromText("public " + aClass.getName() + "() { }", aClass);
        final PsiMethod[] psiMethods = aClass.getMethods();
        PsiMethod firstMethod = (psiMethods.length == 0) ? null : psiMethods [0];
        return (PsiMethod) aClass.addBefore(newConstructor, firstMethod);
      }
      for(PsiMethod method: constructors) {
        if (method.getParameterList().getParameters().length == 0) {
          return method;
        }
      }
      return constructors [0];
    }
  }
}
