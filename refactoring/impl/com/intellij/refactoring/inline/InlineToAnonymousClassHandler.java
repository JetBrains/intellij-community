package com.intellij.refactoring.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class InlineToAnonymousClassHandler {
  public static void invoke(final Project project, final Editor editor, final PsiClass psiClass) {
    String errorMessage = getCannotInlineMessage(psiClass);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.to.anonymous.refactoring"), errorMessage, null, project);
      return;
    }

    InlineToAnonymousClassDialog dlg = new InlineToAnonymousClassDialog(project, psiClass);
    dlg.show();
  }

  @Nullable
  public static String getCannotInlineMessage(final PsiClass psiClass) {
    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return RefactoringBundle.message("inline.to.anonymous.no.abstract");
    }

    if (ClassInheritorsSearch.search(psiClass).findFirst() != null) {
      return RefactoringBundle.message("inline.to.anonymous.no.inheritors");
    }

    final PsiClass[] interfaces = psiClass.getInterfaces();
    if (interfaces.length > 1) {
      return RefactoringBundle.message("inline.to.anonymous.no.multiple.interfaces");
    }
    if (interfaces.length == 1) {
      final PsiClass superClass = psiClass.getSuperClass();
      if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        return RefactoringBundle.message("inline.to.anonymous.no.superclass.and.interface");
      }
    }

    return null;
  }
}