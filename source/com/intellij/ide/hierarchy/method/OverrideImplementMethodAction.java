package com.intellij.ide.hierarchy.method;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.IncorrectOperationException;

abstract class OverrideImplementMethodAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.method.OverrideImplementMethodAction");

  public final void actionPerformed(final AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final MethodHierarchyBrowser methodHierarchyBrowser = (MethodHierarchyBrowser)dataContext.getData(MethodHierarchyBrowser.METHOD_HIERARCHY_BROWSER_DATA_CONSTANT);
    if (methodHierarchyBrowser == null) return;
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;

    final String commandName = event.getPresentation().getText();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {

            try{
              final MethodHierarchyNodeDescriptor[] selectedDescriptors = methodHierarchyBrowser.getSelectedDescriptors();
              for (int i = 0; i < selectedDescriptors.length; i++) {
                OverrideImplementUtil.overrideOrImplement(selectedDescriptors[i].getPsiClass(), methodHierarchyBrowser.getBaseMethod());
              }

              ToolWindowManager.getInstance(project).activateEditorComponent();
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        }, commandName, null);
      }
    });
  }

  public final void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    final MethodHierarchyBrowser methodHierarchyBrowser = (MethodHierarchyBrowser)dataContext.getData(MethodHierarchyBrowser.METHOD_HIERARCHY_BROWSER_DATA_CONSTANT);
    if (methodHierarchyBrowser == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    final MethodHierarchyNodeDescriptor[] selectedDescriptors = methodHierarchyBrowser.getSelectedDescriptors();
    int toImplement = 0;
    int toOverride = 0;

    for (int i = 0; i < selectedDescriptors.length; i++) {
      final MethodHierarchyNodeDescriptor descriptor = selectedDescriptors[i];
      if (canImplementOverride(descriptor, methodHierarchyBrowser, true)) {
        if (toOverride > 0) {
          // no mixed actions allowed
          presentation.setEnabled(false);
          presentation.setVisible(false);
          return;
        }
        toImplement++;
      }
      else if (canImplementOverride(descriptor, methodHierarchyBrowser, false)) {
        if (toImplement > 0) {
          // no mixed actions allowed
          presentation.setEnabled(false);
          presentation.setVisible(false);
          return;
        }
        toOverride++;
      }
      else {
        // no action is applicable to this node
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }
    }

    presentation.setVisible(true);

    update(presentation, toImplement, toOverride);
  }

  protected abstract void update(Presentation presentation, int toImplement, int toOverride);

  private static boolean canImplementOverride(final MethodHierarchyNodeDescriptor descriptor, final MethodHierarchyBrowser methodHierarchyBrowser, final boolean toImplement) {
    final PsiClass psiClass = descriptor.getPsiClass();
    if (psiClass == null) return false;
    final PsiMethod baseMethod = methodHierarchyBrowser.getBaseMethod();
    final MethodSignature signature = MethodSignatureUtil.createMethodSignature(baseMethod.getName(), baseMethod.getParameterList(), baseMethod.getTypeParameterList(), PsiSubstitutor.EMPTY);

    final MethodSignature[] allOriginalSignatures = toImplement ? OverrideImplementUtil.getMethodSignaturesToImplement(psiClass) : OverrideImplementUtil.getMethodSignaturesToOverride(psiClass);
    for (int i = 0; i < allOriginalSignatures.length; i++) {
      final MethodSignature originalSignature = allOriginalSignatures[i];
      if (originalSignature.equals(signature)) {
        return true;
      }
    }

    return false;
  }
}
