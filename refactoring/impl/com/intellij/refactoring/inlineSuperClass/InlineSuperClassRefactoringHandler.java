/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import java.util.Collection;

public class InlineSuperClassRefactoringHandler {
  public static final String REFACTORING_NAME = "Inline Super Class";

  private InlineSuperClassRefactoringHandler() {
  }

  public static void invoke(final Project project, final Editor editor, final PsiClass superClass, Collection<PsiClass> inheritors) {
    if (inheritors.size() > 1) {
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, "Classes which have multiple subclasses cannot be inlined", null, project);
      return;
    }

    new InlineSuperClassRefactoringDialog(project, superClass, inheritors.iterator().next()).show();
  }
}