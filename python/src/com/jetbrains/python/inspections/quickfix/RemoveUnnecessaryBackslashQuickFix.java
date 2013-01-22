package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.editor.PythonEnterHandler;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to remove all unnecessary backslashes in expression
 */
public class RemoveUnnecessaryBackslashQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.remove.unnecessary.backslash");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement != null) {
      PsiElement parent = PsiTreeUtil.getParentOfType(problemElement, PythonEnterHandler.IMPLICIT_WRAP_CLASSES);
      removeBackSlash(parent);
    }
  }
  
  public static void removeBackSlash(PsiElement parent) {
    if (parent != null) {
      Stack<PsiElement> stack = new Stack<PsiElement>();
      if (parent instanceof PyParenthesizedExpression)
        stack.push(((PyParenthesizedExpression)parent).getContainedExpression());
      else
        stack.push(parent);
      while (!stack.isEmpty()) {
        PsiElement el = stack.pop();
        PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(el, PsiWhiteSpace.class);
        if (children != null) {
          for (PsiWhiteSpace ws : children) {
            if (ws.getText().contains("\\")) {
              ws.delete();
            }
          }
        }
        for (PsiElement psiElement : el.getChildren()) {
          stack.push(psiElement);
        }
      }
    }
  } 
}
