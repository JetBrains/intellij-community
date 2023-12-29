// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.patterns;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Provides patterns for literals, strings, arguments and function/method arguments of Python.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see PlatformPatterns
 */
public final class PythonPatterns extends PlatformPatterns {

  private static final int STRING_LITERAL_LIMIT = 10000;

  public static PyElementPattern.Capture<PyLiteralExpression> pyLiteralExpression() {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyLiteralExpression.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
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

  @NotNull
  public static PyElementPattern.Capture<PyExpression> pyArgument(@Nullable String functionName, int index) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return isCallArgument(o, functionName, index);
      }
    });
  }

  @NotNull
  public static PyElementPattern.Capture<PyExpression> pyModuleFunctionArgument(@Nullable String functionName,
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

  @NotNull
  public static PyElementPattern.Capture<PyExpression> pyMethodArgument(@Nullable String functionName,
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

  /**
   * Checks whether the element is assigned to a specific variable via regular expression. The target
   * variable is described in terms of its origin (module.class.member).
   * Example:
   * test.py with the following contents:
   * <pre>
   * {@code
   *   class MyClass():
   *     def __init__(self):
   *       self.abc = "ABC"
   * }
   * </pre>
   * Then the target of the assignment would be described as test.MyClass.abc.
   *
   * @param targetRegexp The regular expression to match.
   * @return The capture for this element pattern.
   */
  @NotNull
  public static PyElementPattern.Capture<PyExpression> pyAssignedTo(@NotNull String targetRegexp) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        var expression = getAssignmentTarget(o);

        if (expression != null) {
          final var typeEvalContext = TypeEvalContext.codeAnalysis(expression.getProject(), expression.getContainingFile());
          final var name = getFullyQualifiedOrigin(expression, typeEvalContext);
          return name != null && name.matches(targetRegexp);
        }

        return false;
      }
    });
  }

  /**
   * Tries to resolve an expression to its origin, such that it ends up with that pattern:
   * module.class.member
   * or
   * module.member
   *
   * @param expression      The expression to resolve.
   * @param typeEvalContext The type evaluation context.
   * @return A string describing the fully qualified origin.
   */
  private static String getFullyQualifiedOrigin(PyExpression expression, TypeEvalContext typeEvalContext) {
    // if it is an attribute of a class, prepend the class' qualified name
    // e.g. "module.Class.attribute"
    if (expression instanceof PyQualifiedExpression qualifiedExpression) {
      final var qualifiedExpressionName = qualifiedExpression.getReferencedName();
      final var qualifier = qualifiedExpression.getQualifier();
      if (qualifier != null && qualifiedExpressionName != null) {
        final var type = typeEvalContext.getType(qualifier);
        PyClassType pyClassType = null;
        if (type instanceof PyClassType cls) {
          pyClassType = cls;
        }
        else if (type instanceof PyModuleType mod) {
          final var modcls = mod.getModuleClassType();
          if (modcls != null) {
            pyClassType = modcls;
          }
        }

        if (pyClassType != null) {
          final var cls = pyClassType.getPyClass();
          final var prop = cls.findProperty(qualifiedExpressionName, true, typeEvalContext);
          if (prop != null) {
            return pyClassType.getClassQName() + "." + prop.getName();
          }
          else {
            final var instAttr = cls.findInstanceAttribute(qualifiedExpressionName, true);
            if (instAttr != null) {
              return pyClassType.getClassQName() + "." + instAttr.getName();
            }
            else {
              final var classAttr = cls.findClassAttribute(qualifiedExpressionName, true, typeEvalContext);
              if (classAttr != null) {
                return pyClassType.getClassQName() + "." + classAttr.getName();
              }
              else {
                final var method = cls.findMethodByName(qualifiedExpressionName, true, typeEvalContext);
                if (method != null) {
                  return pyClassType.getClassQName() + "." + method.getName();
                }
              }
            }
          }
        }
      }
    }

    // fallback to whatever names are available
    if (expression instanceof PyQualifiedNameOwner qualifiedNameOwner) {
      return qualifiedNameOwner.getQualifiedName();
    }
    else if (expression instanceof PsiNamedElement namedElement) {
      return namedElement.getName();
    }

    return null;
  }

  @Nullable
  private static PyExpression getAssignmentTarget(Object expression) {
    if (expression instanceof PyExpression) {
      final var assignment = PsiTreeUtil.getParentOfType((PyExpression)expression, PyAssignmentStatement.class);
      if (assignment != null) {
        for (var mapping : assignment.getTargetsToValuesMapping()) {
          if (mapping.second == expression) {
            return mapping.first;
          }
        }
      }
    }

    return null;
  }

  @NotNull
  private static List<PyCallable> multiResolveCalledFunction(@Nullable Object expression, @Nullable String functionName, int index) {
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
