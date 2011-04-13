package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeReference;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyUsageTypeProvider implements UsageTypeProvider {
  private static final UsageType IN_IMPORT = new UsageType("Usage in import statement");
  private static final UsageType UNTYPED = new UsageType("Untyped (probable) usage");
  private static final UsageType USAGE_IN_ISINSTANCE = new UsageType("Usage in isinstance()");
  private static final UsageType USAGE_IN_SUPERCLASS = new UsageType("Usage in superclass list");

  public UsageType getUsageType(PsiElement element) {
    if (element instanceof PyElement) {
      if (PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null) {
        return IN_IMPORT;
      }
      if (element instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
        if (qualifier != null) {
          final PyType type = qualifier.getType(TypeEvalContext.fast());
          if (type == null || type instanceof PyTypeReference) {
            return UNTYPED;
          }
        }
      }
      if (element instanceof PyReferenceExpression) {
        final PyCallExpression call = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
        if (call != null && call.isCalleeText(PyNames.ISINSTANCE)) {
          final PyExpression[] args = call.getArguments();
          if (args.length == 2) {
            PyExpression typeExpression = args[1];
            if (element == typeExpression) {
              return USAGE_IN_ISINSTANCE;
            }
            typeExpression = PyUtil.flattenParens(typeExpression);
            if (typeExpression instanceof PySequenceExpression && element.getParent() == typeExpression) {
              return USAGE_IN_ISINSTANCE;
            }
          }
        }
        final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        if (pyClass != null && PsiTreeUtil.isAncestor(pyClass.getSuperClassExpressionList(), element, true)) {
          return USAGE_IN_SUPERCLASS;
        }
      }
    }
    return null;
  }
}
