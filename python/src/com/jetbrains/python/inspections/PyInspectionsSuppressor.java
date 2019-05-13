package com.jetbrains.python.inspections;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.inspections.quickfix.PySuppressInspectionFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyInspectionsSuppressor implements InspectionSuppressor {
  private static final Pattern SUPPRESS_PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);
  private static final String PY_INCORRECT_DOCSTRING_INSPECTION_ID = new PyIncorrectDocstringInspection().getID();
  private static final String PY_MISSING_OR_EMPTY_DOCSTRING_INSPECTION_ID = new PyMissingOrEmptyDocstringInspection().getID();
  
  @NotNull
  @Override
  public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    if (PY_INCORRECT_DOCSTRING_INSPECTION_ID.equals(toolId) || PY_MISSING_OR_EMPTY_DOCSTRING_INSPECTION_ID.equals(toolId)) {
      return new SuppressQuickFix[]{
        new PySuppressInspectionFix(toolId, "Suppress for function", PyFunction.class),
        new PySuppressInspectionFix(toolId, "Suppress for class", PyClass.class)
      };
    }
    else {
      return new SuppressQuickFix[]{
        new PySuppressInspectionFix(toolId, "Suppress for statement", PyStatement.class) {
          @Override
          public PsiElement getContainer(PsiElement context) {
            if (PsiTreeUtil.getParentOfType(context, PyStatementList.class, false, ScopeOwner.class) != null ||
                PsiTreeUtil.getParentOfType(context, PyFunction.class, PyClass.class) == null) {
              return super.getContainer(context);
            }
            return null;
          }
        },
        new PySuppressInspectionFix(toolId, "Suppress for function", PyFunction.class),
        new PySuppressInspectionFix(toolId, "Suppress for class", PyClass.class)
      };
    }
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    return isSuppressedForParent(element, PyStatement.class, toolId) ||
           isSuppressedForParent(element, PyFunction.class, toolId) ||
           isSuppressedForParent(element, PyClass.class, toolId);
  }

  private static boolean isSuppressedForParent(@NotNull PsiElement element,
                                               @NotNull final Class<? extends PyElement> parentClass,
                                               @NotNull String suppressId) {
    PyElement parent = PsiTreeUtil.getParentOfType(element, parentClass, false);
    if (parent == null) {
      return false;
    }
    return isSuppressedForElement(parent, suppressId);
  }

  private static boolean isSuppressedForElement(@NotNull PyElement stmt, @NotNull String suppressId) {
    PsiElement prevSibling = stmt.getPrevSibling();
    if (prevSibling == null) {
      final PsiElement parent = stmt.getParent();
      if (parent != null) {
        prevSibling = parent.getPrevSibling();
      }
    }
    while (prevSibling instanceof PsiComment || prevSibling instanceof PsiWhiteSpace) {
      if (prevSibling instanceof PsiComment && isSuppressedInComment(prevSibling.getText().substring(1).trim(), suppressId)) {
        return true;
      }
      prevSibling = prevSibling.getPrevSibling();
    }
    return false;
  }

  private static boolean isSuppressedInComment(@NotNull String commentText, @NotNull String suppressId) {
    Matcher m = SUPPRESS_PATTERN.matcher(commentText);
    return m.matches() && SuppressionUtil.isInspectionToolIdMentioned(m.group(1), suppressId);
  }
}
