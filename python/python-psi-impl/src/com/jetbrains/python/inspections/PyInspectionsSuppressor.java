package com.jetbrains.python.inspections;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.inspections.quickfix.PySuppressInspectionFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyInspectionsSuppressor implements InspectionSuppressor {
  private static final String PY_INCORRECT_DOCSTRING_INSPECTION_ID = new PyIncorrectDocstringInspection().getID();
  private static final String PY_MISSING_OR_EMPTY_DOCSTRING_INSPECTION_ID = new PyMissingOrEmptyDocstringInspection().getID();

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    // Insert the industry-standard kebab-case alias (e.g. `method-overriding`) when the inspection has one,
    // so that is the form users adopt; the legacy `PyMethodOverriding` id keeps working for recognition.
    final String suppressId = suppressId(toolId);
    if (PY_INCORRECT_DOCSTRING_INSPECTION_ID.equals(toolId) || PY_MISSING_OR_EMPTY_DOCSTRING_INSPECTION_ID.equals(toolId)) {
      return new SuppressQuickFix[]{
        new PySuppressInspectionFix(suppressId, PyPsiBundle.message("INSP.python.suppressor.suppress.for.function"), PyFunction.class),
        new PySuppressInspectionFix(suppressId, PyPsiBundle.message("INSP.python.suppressor.suppress.for.class"), PyClass.class)
      };
    }
    else {
      return new SuppressQuickFix[]{
        new PySuppressInspectionFix(suppressId, PyPsiBundle.message("INSP.python.suppressor.suppress.for.statement"), PyStatement.class) {
          @Override
          public PsiElement getContainer(PsiElement context) {
            if (PsiTreeUtil.getParentOfType(context, PyStatementList.class, false, ScopeOwner.class) != null ||
                PsiTreeUtil.getParentOfType(context, PyFunction.class, PyClass.class) == null) {
              return super.getContainer(context);
            }
            return null;
          }
        },
        new PySuppressInspectionFix(suppressId, PyPsiBundle.message("INSP.python.suppressor.suppress.for.function"), PyFunction.class),
        new PySuppressInspectionFix(suppressId, PyPsiBundle.message("INSP.python.suppressor.suppress.for.class"), PyClass.class)
      };
    }
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    if (PySuppressionUtil.INSTANCE.isSuppressed(element, toolId)) {
      return true;
    }
    final String code = PySuppressionUtil.INSTANCE.toSuppressionCode(toolId);
    return code != null && PySuppressionUtil.INSTANCE.isSuppressed(element, code);
  }

  private static @NotNull String suppressId(@NotNull String toolId) {
    final String code = PySuppressionUtil.INSTANCE.toSuppressionCode(toolId);
    return code != null ? code : toolId;
  }
}
