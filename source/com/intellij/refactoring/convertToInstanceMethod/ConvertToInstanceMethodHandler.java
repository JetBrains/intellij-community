package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler");
  static final String REFACTORING_NAME = "Convert To Instance Method";

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiMethod)) {
      String message = "Cannot perform the refactoring.\n" +
              "The caret should be positioned at the name of the method to be refactored.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeMethodStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod)) return;
    final PsiMethod method = ((PsiMethod)elements[0]);
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      String message = "Cannot perform the refactoring\n" +
                       "Method " + method.getName() + " is not static.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    List<PsiParameter> suitableParameters = new ArrayList<PsiParameter>();
    boolean classTypesFound = false;
    boolean resolvableClassesFound = false;
    boolean classesInProjectFound = false;
    for (int i = 0; i < parameters.length; i++) {
      final PsiParameter parameter = parameters[i];
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        classTypesFound = true;
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null) {
          resolvableClassesFound = true;
          final boolean inProject = method.getManager().isInProject(psiClass);
          if (inProject) {
            classesInProjectFound = true;
            suitableParameters.add(parameter);
          }
        }
      }
    }
    if (suitableParameters.isEmpty()) {
      String message = null;
      if (!classTypesFound) {
        message = "There are no parameters that have a reference type";
      }
      else if (!resolvableClassesFound) {
        message = "All reference type parametres have unknown types";
      }
      else if (!classesInProjectFound) {
        message = "All reference type parameters have types that are not in project";
      }
      LOG.assertTrue(message != null);
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME,
                                              "Cannot perform refactoring.\n" + message,
                                              HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }
    final ConvertToInstanceMethodDialog dialog = new ConvertToInstanceMethodDialog(
      method,
      (PsiParameter[])suitableParameters.toArray(new PsiParameter[suitableParameters.size()]));
    dialog.show();
  }
}
