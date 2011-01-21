package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 19.02.2010
 * Time: 18:50:24
 */
public class ReplaceBuiltinsQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.convert.builtin.import");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.builtin");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiElement element = descriptor.getPsiElement();
    PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
    for (PyImportElement importElement : importStatement.getImportElements()) {
      PyReferenceExpression importReference = importElement.getImportReference();
      if (importReference != null) {
        if (LanguageLevel.forFile(element.getContainingFile().getVirtualFile()).isPy3K()) {
          if ("__builtin__".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createFromText(LanguageLevel.getDefault(), PyReferenceExpression.class, "builtins"));
          }
        } else {
          if ("builtins".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createFromText(LanguageLevel.getDefault(), PyReferenceExpression.class, "__builtin__"));
          }
        }
      }
    }

  }
}
