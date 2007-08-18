package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler");
  static final String REFACTORING_NAME = RefactoringBundle.message("convert.to.instance.method.title");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiMethod)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeMethodStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod)) return;
    final PsiMethod method = ((PsiMethod)elements[0]);
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      String message = RefactoringBundle.message("convertToInstanceMethod.method.is.not.static", method.getName());
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    List<PsiParameter> suitableParameters = new ArrayList<PsiParameter>();
    boolean classTypesFound = false;
    boolean resolvableClassesFound = false;
    boolean classesInProjectFound = false;
    for (final PsiParameter parameter : parameters) {
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        classTypesFound = true;
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
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
        message = RefactoringBundle.message("convertToInstanceMethod.no.parameters.with.reference.type");
      }
      else if (!resolvableClassesFound) {
        message = RefactoringBundle.message("convertToInstanceMethod.all.reference.type.parametres.have.unknown.types");
      }
      else if (!classesInProjectFound) {
        message = RefactoringBundle.message("convertToInstanceMethod.all.reference.type.parameters.are.not.in.project");
      }
      LOG.assertTrue(message != null);
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME,
                                              RefactoringBundle.getCannotRefactorMessage(message),
                                              HelpID.CONVERT_TO_INSTANCE_METHOD, project);
      return;
    }

    new ConvertToInstanceMethodDialog(
      method,
      suitableParameters.toArray(new PsiParameter[suitableParameters.size()])).show();
  }
}
