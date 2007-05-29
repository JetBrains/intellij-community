package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;

/**
 * @author yole
 */
public class InlineToAnonymousClassHandler {
  public static void invoke(final Project project, final Editor editor, final PsiClass psiClass) {
    InlineToAnonymousClassDialog dlg = new InlineToAnonymousClassDialog(project, psiClass);
    dlg.show();
  }
}