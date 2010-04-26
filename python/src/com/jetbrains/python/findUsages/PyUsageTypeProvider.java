package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyUsageTypeProvider implements UsageTypeProvider {
  private static final UsageType IN_IMPORT = new UsageType("Usage in import statement");
  private static final UsageType UNTYPED = new UsageType("Untyped (probable) usage");

  public UsageType getUsageType(PsiElement element) {
    if (element instanceof PyElement) {
      if (PsiTreeUtil.getParentOfType(element, PyImportStatement.class, PyFromImportStatement.class) != null) {
        return IN_IMPORT;
      }
      if (element instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
        if (qualifier != null && qualifier.getType(TypeEvalContext.fast()) == null) {
          return UNTYPED;
        }
      }
    }
    return null;
  }
}
