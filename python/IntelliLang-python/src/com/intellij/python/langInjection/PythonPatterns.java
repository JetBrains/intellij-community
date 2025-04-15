// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.langInjection;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.ast.PyAstStringElement;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.patterns.PyElementPattern;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.jetbrains.python.codeInsight.template.PyHTMLInjectionControllerKt.looksLikeHTML;

/**
 * Provides patterns for literals, strings, arguments and function/method arguments of Python.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see PlatformPatterns
 */

@ApiStatus.Internal
public final class PythonPatterns extends PlatformPatterns {

  private static final int STRING_LITERAL_LIMIT = 10000;

  public static PyElementPattern.Capture<PyLiteralExpression> pyLiteralExpression() {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyLiteralExpression.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o instanceof PyLiteralExpression;
      }
    });
  }

  public static PyElementPattern.Capture<PyStringLiteralExpression> pyStringLiteralMatches(final String regexp) {
    final Pattern pattern = Pattern.compile(regexp, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyStringLiteralExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        if (o instanceof PyStringLiteralExpression expr) {
          if (!DocStringUtil.isDocStringExpression(expr) && expr.getTextLength() < STRING_LITERAL_LIMIT) {
            final String value = expr.getStringValue();
            return pattern.matcher(value).find();
          }
        }
        return false;
      }
    });
  }

  public static @NotNull PyElementPattern.Capture<PyExpression> pyArgument(@Nullable String functionName, int index) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return isCallArgument(o, functionName, index);
      }
    });
  }

  public static @NotNull PyElementPattern.Capture<PyFormattedStringElement> templateOrFormattedStringContainsHTML() {
    return new PyElementPattern.Capture<>(PyFormattedStringElement.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        if (o instanceof PyStringLiteralExpression stringLiteralExpression && stringLiteralExpression.getStringElements().size() == 1) {
          PyAstStringElement stringElement = stringLiteralExpression.getStringElements().get(0);
          return stringElement.isTemplate() && looksLikeHTML(stringElement.getContent());
        }
        return false;
      }
    };
  }

  public static @NotNull PyElementPattern.Capture<PyExpression> pyModuleFunctionArgument(@Nullable String functionName,
                                                                                         int index,
                                                                                         @NotNull String moduleName) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return StreamEx
          .of(multiResolveCalledFunction(o, functionName, index))
          .select(PyFunction.class)
          .map(ScopeUtil::getScopeOwner)
          .select(PyFile.class)
          .anyMatch(file -> moduleName.equals(FileUtilRt.getNameWithoutExtension(file.getName())));
      }
    });
  }

  public static @NotNull PyElementPattern.Capture<PyExpression> pyMethodArgument(@Nullable String functionName,
                                                                                 int index,
                                                                                 @NotNull String classQualifiedName) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyExpression.class) {
      @Override
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

  private static @NotNull List<PyCallable> multiResolveCalledFunction(@Nullable Object expression, @Nullable String functionName, int index) {
    if (!isCallArgument(expression, functionName, index)) {
      return Collections.emptyList();
    }

    final PyCallExpression call = (PyCallExpression)((PyExpression)expression).getParent().getParent();

    // TODO is it better or worse to allow implicits here?
    final var context = TypeEvalContext.codeAnalysis(call.getProject(), call.getContainingFile());

    return call.multiResolveCalleeFunction(PyResolveContext.defaultContext(context));
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
