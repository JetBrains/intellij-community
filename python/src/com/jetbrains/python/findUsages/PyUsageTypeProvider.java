// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProviderEx;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


public final class PyUsageTypeProvider implements UsageTypeProviderEx {
  private static final UsageType IN_IMPORT = new UsageType(PyPsiBundle.messagePointer("python.find.usages.usage.in.import.statement"));
  private static final UsageType UNTYPED = new UsageType(PyPsiBundle.messagePointer("python.find.usages.untyped.probable.usage"));
  private static final UsageType USAGE_IN_ISINSTANCE = new UsageType(PyPsiBundle.messagePointer("python.find.usages.usage.in.isinstance"));
  private static final UsageType USAGE_IN_SUPERCLASS = new UsageType(PyPsiBundle.messagePointer("python.find.usages.usage.in.superclass.list"));
  private static final UsageType USAGE_IN_TYPE_HINT = new UsageType(PyPsiBundle.messagePointer("python.find.usages.usage.in.type.hint"));

  @Override
  public UsageType getUsageType(@NotNull PsiElement element) {
    return getUsageType(element, UsageTarget.EMPTY_ARRAY);
  }

  @Override
  public UsageType getUsageType(PsiElement element, UsageTarget @NotNull [] targets) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    if (element instanceof PyElement) {
      if (PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null) {
        return IN_IMPORT;
      }
      if (element instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
        if (qualifier != null) {
          final PyType type = context.getType(qualifier);
          if (type == null || type instanceof PyStructuralType) {
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
            typeExpression = PyPsiUtils.flattenParens(typeExpression);
            if (typeExpression instanceof PySequenceExpression && element.getParent() == typeExpression) {
              return USAGE_IN_ISINSTANCE;
            }
          }
        }
        if (PyTypingTypeProvider.isInsideTypeHint(element, context)) {
          return USAGE_IN_TYPE_HINT;
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
