package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * Appends missing {@code return None}, and transforms {@code return} into {@code return None}.
 */
public class PyMakeReturnsExplicitFix extends PsiUpdateModCommandAction<PyFunction> {

  public PyMakeReturnsExplicitFix(@NotNull PyFunction function) {
    super(function);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyFunction element, @NotNull ModPsiUpdater updater) {
    var returnPoints = element.getReturnPoints(TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));
    for (var point : returnPoints) {
      makeExplicit(point);
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.return.stmts.explicit");
  }

  private static void makeExplicit(@NotNull PyStatement stmt) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(stmt.getProject());
    LanguageLevel languageLevel = LanguageLevel.forElement(stmt);

    var returnStmt = elementGenerator.createFromText(languageLevel, PyReturnStatement.class, "return None");
    
    if ((stmt instanceof PyReturnStatement ret && ret.getExpression() == null) || (stmt instanceof PyPassStatement)) {
      stmt.replace(returnStmt);
    }
    else if (!(stmt instanceof PyReturnStatement)) {
      stmt.getParent().addAfter(returnStmt, stmt);
    }
  }
}
