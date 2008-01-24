package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.Nullable;

public class JavaRefactoringElementDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (!(location instanceof RefactoringDescriptionLocation)) return null;
    RefactoringDescriptionLocation rdLocation = (RefactoringDescriptionLocation) location;

    if (element instanceof PsiField) {
      int options = PsiFormatUtil.SHOW_NAME;
      if (rdLocation.includeParent()) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      return RefactoringBundle.message("field.description", CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatVariable((PsiVariable)element, options, PsiSubstitutor.EMPTY)));
    }

    if (element instanceof PsiMethod) {
      int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS;
      if (rdLocation.includeParent()) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      final PsiMethod method = (PsiMethod) element;
      return method.isConstructor() ?
             RefactoringBundle.message("constructor.description", CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtil.SHOW_TYPE))) :
             RefactoringBundle.message("method.description", CommonRefactoringUtil.htmlEmphasize( PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtil.SHOW_TYPE)));
    }

    if (element instanceof PsiClassInitializer) {
      PsiClassInitializer initializer = (PsiClassInitializer) element;
      boolean isStatic = initializer.hasModifierProperty(PsiModifier.STATIC);
      return isStatic
             ? RefactoringBundle.message("static.initializer.description", getElementDescription(initializer.getContainingClass(), RefactoringDescriptionLocation.WITHOUT_PARENT))
             : RefactoringBundle.message("instance.initializer.description", getElementDescription(initializer.getContainingClass(), RefactoringDescriptionLocation.WITHOUT_PARENT));
    }

    if (element instanceof PsiParameter) {
      return RefactoringBundle.message("parameter.description", CommonRefactoringUtil.htmlEmphasize(((PsiParameter)element).getName()));
    }

    if (element instanceof PsiLocalVariable) {
      return RefactoringBundle.message("local.variable.description", CommonRefactoringUtil.htmlEmphasize(((PsiVariable)element).getName()));
    }

    if (element instanceof PsiPackage) {
      return RefactoringBundle.message("package.description", CommonRefactoringUtil.htmlEmphasize(((PsiPackage)element).getName()));
    }

    if ((element instanceof PsiClass)) {
      //TODO : local & anonymous
      PsiClass psiClass = (PsiClass) element;
      return RefactoringBundle.message("class.description", CommonRefactoringUtil.htmlEmphasize(UsageViewUtil.getDescriptiveName(psiClass)));
    }
    return null;
  }
}
