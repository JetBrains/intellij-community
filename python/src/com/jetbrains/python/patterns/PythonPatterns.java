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
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
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

  @NotNull
  public static PyElementPattern.Capture<PyExpression> pyArgument(@Nullable String functionName, int index) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return isCallArgument(o, functionName, index);
      }
    });
  }

  @NotNull
  public static PyElementPattern.Capture<PyExpression> pyModuleFunctionArgument(@Nullable String functionName,
                                                                                int index,
                                                                                @NotNull String moduleName) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return StreamEx
          .of(multiResolveCalledFunction(o, functionName, index))
          .select(PyFunction.class)
          .map(ScopeUtil::getScopeOwner)
          .select(PyFile.class)
          .anyMatch(file -> moduleName.equals(FileUtil.getNameWithoutExtension(file.getName())));
      }
    });
  }

  @NotNull
  public static PyElementPattern.Capture<PyExpression> pyMethodArgument(@Nullable String functionName,
                                                                        int index,
                                                                        @NotNull String classQualifiedName) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return StreamEx
          .of(multiResolveCalledFunction(o, functionName, index))
          .select(PyFunction.class)
          .map(ScopeUtil::getScopeOwner)
          .select(PyClass.class)
          .anyMatch(cls -> classQualifiedName.equals(cls.getQualifiedName()));
      }
    });
  }

  @NotNull
  private static List<PyCallable> multiResolveCalledFunction(@Nullable Object expression, @Nullable String functionName, int index) {
    if (!isCallArgument(expression, functionName, index)) {
      return Collections.emptyList();
    }

    final PyCallExpression call = (PyCallExpression)((PyExpression)expression).getParent().getParent();

    // TODO is it better or worse to allow implicits here?
    final PyResolveContext context = PyResolveContext
      .noImplicits()
      .withTypeEvalContext(TypeEvalContext.codeAnalysis(call.getProject(), call.getContainingFile()));

    return call.multiResolveCalleeFunction(context);
  }

  private static boolean isCallArgument(@Nullable Object expression, @Nullable String functionName, int index) {
    if (!(expression instanceof PyExpression)) {
      return false;
    }

    final PsiElement argumentList = ((PyExpression)expression).getParent();
    if (!(argumentList instanceof PyArgumentList)) {
      return false;
    }

    final PsiElement call = argumentList.getParent();
    if (!(call instanceof PyCallExpression)) {
      return false;
    }

    final PyExpression referenceToCallee = ((PyCallExpression)call).getCallee();
    if (!(referenceToCallee instanceof PyReferenceExpression)) {
      return false;
    }

    final String referencedName = ((PyReferenceExpression)referenceToCallee).getReferencedName();
    if (referencedName == null || !referencedName.equals(functionName)) {
      return false;
    }

    final PsiElement[] arguments = argumentList.getChildren();
    return index < arguments.length && expression == arguments[index];
  }
}
