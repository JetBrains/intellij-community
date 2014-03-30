/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProviderEx;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.CallArgumentsMappingImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyUsageTypeProvider implements UsageTypeProviderEx {
  private static final UsageType IN_IMPORT = new UsageType("Usage in import statement");
  private static final UsageType UNTYPED = new UsageType("Untyped (probable) usage");
  private static final UsageType USAGE_IN_ISINSTANCE = new UsageType("Usage in isinstance()");
  private static final UsageType USAGE_IN_SUPERCLASS = new UsageType("Usage in superclass list");
  private static final UsageType SIGNATURE_MISMATCH = new UsageType("Untyped (probable) usage, signature mismatch");

  @Override
  public UsageType getUsageType(PsiElement element) {
    return getUsageType(element, UsageTarget.EMPTY_ARRAY);
  }

  public UsageType getUsageType(PsiElement element, @NotNull UsageTarget[] targets) {
    if (element instanceof PyElement) {
      if (PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null) {
        return IN_IMPORT;
      }
      if (element instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
        if (qualifier != null) {
          final TypeEvalContext context = TypeEvalContext.userInitiated(element.getContainingFile());
          final PyType type = context.getType(qualifier);
          if (type == null) {
            final PyCallExpression call = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
            if (call != null && element == call.getCallee()) {
              return checkMatchingSignatureGroup(call, targets, context);
            }
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
        final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        if (pyClass != null && PsiTreeUtil.isAncestor(pyClass.getSuperClassExpressionList(), element, true)) {
          return USAGE_IN_SUPERCLASS;
        }
      }
    }
    return null;
  }

  @Nullable
  private static UsageType checkMatchingSignatureGroup(PyCallExpression call, UsageTarget[] targets, @NotNull TypeEvalContext context) {
    if (targets.length == 1 && targets[0] instanceof PsiElementUsageTarget) {
      final PsiElement element = ((PsiElementUsageTarget)targets[0]).getElement();
      if (element instanceof PyFunction) {
        PyFunction function = (PyFunction)element;
        final PyFunction.Modifier modifier = function.getModifier();
        PyCallExpression.PyMarkedCallee callee = new PyCallExpression.PyMarkedCallee(function, modifier, 1, true);
        CallArgumentsMappingImpl mapping = new CallArgumentsMappingImpl(call.getArgumentList());
        mapping.mapArguments(callee, context);
        if (mapping.hasProblems()) {
          return SIGNATURE_MISMATCH;
        }
      }
    }
    return null;
  }
}
