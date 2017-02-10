/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.patterns;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonPatterns extends PlatformPatterns {

  private static final int STRING_LITERAL_LIMIT = 10000;

  public static PyElementPattern.Capture<PyLiteralExpression> pyLiteralExpression() {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyLiteralExpression>(PyLiteralExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof PyLiteralExpression;
      }
    });
  }

  public static PyElementPattern.Capture<PyStringLiteralExpression> pyStringLiteralMatches(final String regexp) {
    final Pattern pattern = Pattern.compile(regexp, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyStringLiteralExpression>(PyStringLiteralExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        if (o instanceof PyStringLiteralExpression) {
          final PyStringLiteralExpression expr = (PyStringLiteralExpression)o;
          if (!DocStringUtil.isDocStringExpression(expr) && expr.getTextLength() < STRING_LITERAL_LIMIT) {
            final String value = expr.getStringValue();
            return pattern.matcher(value).find();
          }
        }
        return false;
      }
    });
  }

  public static PyElementPattern.Capture<PyExpression> pyArgument(final String functionName, final int index) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return isCallArgument(o, functionName, index);
      }
    });
  }

  public static PyElementPattern.Capture<PyExpression> pyModuleFunctionArgument(final String functionName, final int index, final String moduleName) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        PyCallable function = resolveCalledFunction(o, functionName, index);
        if (!(function instanceof PyFunction)) {
          return false;
        }
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class);
        if (!(scopeOwner instanceof PyFile)) {
          return false;
        }
        return moduleName.equals(FileUtil.getNameWithoutExtension(scopeOwner.getName()));
      }
    });
  }

  public static PyElementPattern.Capture<PyExpression> pyMethodArgument(final String functionName, final int index, final String classQualifiedName) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        PyCallable function = resolveCalledFunction(o, functionName, index);
        if (!(function instanceof PyFunction)) {
          return false;
        }
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class);
        if (!(scopeOwner instanceof PyClass)) {
          return false;
        }
        return classQualifiedName.equals(((PyClass)scopeOwner).getQualifiedName());
      }
    });
  }

  private static PyCallable resolveCalledFunction(Object o, String functionName, int index) {
    if (!isCallArgument(o, functionName, index)) {
      return null;
    }
    PyExpression expression = (PyExpression) o;
    PyCallExpression call = (PyCallExpression) expression.getParent().getParent();

    // TODO is it better or worse to allow implicits here?
    PyResolveContext context = PyResolveContext.noImplicits()
      .withTypeEvalContext(TypeEvalContext.codeAnalysis(expression.getProject(), expression.getContainingFile()));

    PyCallExpression.PyMarkedCallee callee = call.resolveCallee(context);
    return callee != null ? callee.getCallable() : null;
  }

  private static boolean isCallArgument(Object o, String functionName, int index) {
    if (!(o instanceof PyExpression)) {
      return false;
    }
    PsiElement parent = ((PyExpression)o).getParent();
    if (!(parent instanceof PyArgumentList)) {
      return false;
    }
    PsiElement parent1 = parent.getParent();
    if (!(parent1 instanceof PyCallExpression)) {
      return false;
    }
    PyExpression methodExpression = ((PyCallExpression)parent1).getCallee();
    if (!(methodExpression instanceof PyReferenceExpression)) {
      return false;
    }
    String referencedName = ((PyReferenceExpression)methodExpression).getReferencedName();
    if (referencedName == null || !referencedName.equals(functionName)) {
      return false;
    }
    int i = 0;
    for (PsiElement child : parent.getChildren()) {
      if (i == index) {
        return child == o;
      }
      i++;
    }
    return false;
  }
}
