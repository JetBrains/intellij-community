package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyDeprecationInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Deprecated function, class or module";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      PsiElement resolveResult = node.getReference(PyResolveContext.noImplicits()).resolve();
      String deprecationMessage = null;
      if (resolveResult instanceof PyFunction) {
        deprecationMessage = ((PyFunction) resolveResult).getDeprecationMessage();
      }
      else if (resolveResult instanceof PyFile) {
        deprecationMessage = ((PyFile)resolveResult).getDeprecationMessage();
      }
      if (deprecationMessage != null) {
        ASTNode nameElement = node.getNameElement();
        registerProblem(nameElement == null ? node : nameElement.getPsi(), deprecationMessage, ProblemHighlightType.LIKE_DEPRECATED, null);
      }
    }
  }
}
