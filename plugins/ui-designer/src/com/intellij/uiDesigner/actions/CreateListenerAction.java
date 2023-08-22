// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


public class CreateListenerAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance(CreateListenerAction.class);

  @Override
  protected void actionPerformed(final GuiEditor editor, final List<? extends RadComponent> selection, final AnActionEvent e) {
    final DefaultActionGroup actionGroup = prepareActionGroup(selection);
    final JComponent selectedComponent = selection.get(0).getDelegee();
    final DataContext context = DataManager.getInstance().getDataContext(selectedComponent);
    final JBPopupFactory factory = JBPopupFactory.getInstance();
    final ListPopup popup = factory.createActionGroupPopup(UIDesignerBundle.message("create.listener.title"), actionGroup, context,
                                                           JBPopupFactory.ActionSelectionAid.NUMBERING, true);

    FormEditingUtil.showPopupUnderComponent(popup, selection.get(0));
  }

  private DefaultActionGroup prepareActionGroup(final List<? extends RadComponent> selection) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final EventSetDescriptor[] eventSetDescriptors;
    try {
      BeanInfo beanInfo = Introspector.getBeanInfo(selection.get(0).getComponentClass());
      eventSetDescriptors = beanInfo.getEventSetDescriptors();
    }
    catch (IntrospectionException e) {
      LOG.error(e);
      return null;
    }
    EventSetDescriptor[] sortedDescriptors = eventSetDescriptors.clone();
    Arrays.sort(sortedDescriptors, Comparator.comparing(o -> o.getListenerType().getName()));
    for(EventSetDescriptor descriptor: sortedDescriptors) {
      actionGroup.add(new MyCreateListenerAction(selection, descriptor));
    }
    return actionGroup;
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<? extends RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(canCreateListener(selection));
  }

  private static boolean canCreateListener(final ArrayList<? extends RadComponent> selection) {
    if (selection.size() == 0) return false;
    final RadRootContainer root = (RadRootContainer)FormEditingUtil.getRoot(selection.get(0));
    if (root.getClassToBind() == null) return false;
    String componentClass = selection.get(0).getComponentClassName();
    for(RadComponent c: selection) {
      if (!c.getComponentClassName().equals(componentClass) || c.getBinding() == null) return false;
      if (BindingProperty.findBoundField(root, c.getBinding()) == null) return false;
    }
    return true;
  }

  private static @NlsSafe String getEventDescriptorName(EventSetDescriptor descriptor) {
    return descriptor.getListenerType().getSimpleName();
  }

  private static class MyCreateListenerAction extends AnAction {
    private final List<? extends RadComponent> mySelection;
    private final EventSetDescriptor myDescriptor;
    @NonNls private static final String LISTENER_SUFFIX = "Listener";
    @NonNls private static final String ADAPTER_SUFFIX = "Adapter";

    MyCreateListenerAction(final List<? extends RadComponent> selection, EventSetDescriptor descriptor) {
      super(getEventDescriptorName(descriptor));
      mySelection = selection;
      myDescriptor = descriptor;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      CommandProcessor.getInstance().executeCommand(e.getProject(), () -> createListener(), UIDesignerBundle.message("create.listener.command"), null);
    }

    private void createListener() {
      RadRootContainer root = (RadRootContainer)FormEditingUtil.getRoot(mySelection.get(0));
      final PsiField[] boundFields = new PsiField[mySelection.size()];
      for (int i = 0; i < mySelection.size(); i++) {
        boundFields[i] = BindingProperty.findBoundField(root, mySelection.get(i).getBinding());
      }
      final PsiClass myClass = boundFields[0].getContainingClass();

      if (!FileModificationService.getInstance().preparePsiElementForWrite(myClass)) return;

      try {
        PsiMethod constructor = findConstructorToInsert(myClass);
        final Module module = ModuleUtil.findModuleForPsiElement(myClass);
        PsiClass listenerClass = null;
        final String listenerClassName = myDescriptor.getListenerType().getName();
        if (listenerClassName.endsWith(LISTENER_SUFFIX)) {
          String adapterClassName = listenerClassName.substring(0, listenerClassName.length() - LISTENER_SUFFIX.length()) + ADAPTER_SUFFIX;
          listenerClass = JavaPsiFacade.getInstance(myClass.getProject())
            .findClass(adapterClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
        }
        if (listenerClass == null) {
          listenerClass = JavaPsiFacade.getInstance(myClass.getProject())
            .findClass(listenerClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
        }
        if (listenerClass == null) {
          Messages.showErrorDialog(myClass.getProject(), UIDesignerBundle.message("create.listener.class.not.found"), CommonBundle.getErrorTitle());
          return;
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        final PsiCodeBlock body = constructor.getBody();
        LOG.assertTrue(body != null);

        @NonNls StringBuilder builder = new StringBuilder();
        @NonNls String variableName;
        if (boundFields.length == 1) {
          variableName = null;
          builder.append(boundFields[0].getName());
          builder.append(".");
          builder.append(myDescriptor.getAddListenerMethod().getName());
          builder.append("(");
        }
        else {
          builder.append(listenerClass.getQualifiedName()).append(" ");
          if (body.getLastBodyElement() == null) {
            variableName = "listener";
          }
          else {
            final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myClass.getProject());
            variableName = codeStyleManager.suggestUniqueVariableName("listener", body.getLastBodyElement(), false);
          }
          builder.append(variableName).append("=");
        }
        builder.append("new ");
        builder.append(listenerClass.getQualifiedName());
        builder.append("() { } ");
        if (boundFields.length == 1) {
          builder.append(");");
        }
        else {
          builder.append(";");
        }

        PsiStatement stmt = WriteAction.compute(() -> {
          PsiStatement temp = factory.createStatementFromText(builder.toString(), constructor);
          temp = (PsiStatement)body.addAfter(temp, body.getLastBodyElement());
          temp = (PsiStatement)JavaCodeStyleManager.getInstance(body.getProject()).shortenClassReferences(temp);
          if (boundFields.length > 1) {
            PsiElement anchor = temp;
            for (PsiField field : boundFields) {
              PsiElement addStmt = factory
                .createStatementFromText(field.getName() + "." + myDescriptor.getAddListenerMethod().getName() + "(" + variableName + ");",
                                         constructor);
              addStmt = body.addAfter(addStmt, anchor);
              anchor = addStmt;
            }
          }
          return temp;
        });
        
        final SmartPsiElementPointer ptr = SmartPointerManager.getInstance(myClass.getProject()).createSmartPsiElementPointer(stmt);
        final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myClass);
        final FileEditor[] fileEditors =
          virtualFile != null ? FileEditorManager.getInstance(myClass.getProject()).openFile(virtualFile, true, true) : null;
        IdeFocusManager.findInstance().doWhenFocusSettlesDown(new Runnable() {
          @Override
          public void run() {
            final PsiElement anonymousClassStatement = ptr.getElement();
            if (anonymousClassStatement == null) {
              return;
            }

            final Ref<PsiClass> newClassRef = new Ref<>();
            anonymousClassStatement.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitClass(@NotNull PsiClass aClass) {
                newClassRef.set(aClass);
              }
            });
            final PsiClass newClass = newClassRef.get();

            final Editor editor = getEditor();
            if (editor != null && newClass != null) {
              PsiElement brace = newClass.getLBrace();
              if (brace != null) {
                editor.getCaretModel().moveToOffset(brace.getTextOffset());
              }
              CommandProcessor.getInstance().executeCommand(myClass.getProject(), () -> {
                if (!OverrideImplementExploreUtil.getMethodSignaturesToImplement(newClass).isEmpty()) {
                  OverrideImplementUtil.chooseAndImplementMethods(newClass.getProject(), editor, newClass);
                }
                else {
                  OverrideImplementUtil.chooseAndOverrideMethods(newClass.getProject(), editor, newClass);
                }
              }, "", null);
            }
          }

          private Editor getEditor() {
            if (fileEditors != null) {
              for (FileEditor fileEditor : fileEditors) {
                if (fileEditor instanceof TextEditor) {
                  return ((TextEditor)fileEditor).getEditor();
                }
              }
            }
            return null;
          }
        }, ModalityState.current());
      }
      catch (IncorrectOperationException ex) {
        LOG.error(ex);
      }
    }

    private PsiMethod findConstructorToInsert(final PsiClass aClass) throws IncorrectOperationException {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
        PsiMethod newConstructor = factory.createMethodFromText("public " + aClass.getName() + "() { }", aClass);
        final PsiMethod[] psiMethods = aClass.getMethods();
        PsiMethod firstMethod = (psiMethods.length == 0) ? null : psiMethods [0];
        return (PsiMethod) aClass.addBefore(newConstructor, firstMethod);
      }
      for(PsiMethod method: constructors) {
        if (method.getParameterList().isEmpty()) {
          return method;
        }
      }
      return constructors [0];
    }
  }
}
