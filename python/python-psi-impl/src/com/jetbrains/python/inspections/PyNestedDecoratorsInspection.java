// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.RemoveDecoratorQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Checks nested decorators, especially whatever comes after @classmethod.
 * <br/>
 */
public final class PyNestedDecoratorsInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    private static final Set<String> TRANSFORMING_DECORATORS = Set.of(PyNames.CLASSMETHOD, PyNames.STATICMETHOD);
    private static final Set<String> UNAFFECTED_DECORATORS = Set.of(
      "typing.final", "typing.no_type_check", "typing.overload", "typing.override", "typing.type_check_only",
      "typing_extensions.final", "typing_extensions.no_type_check", "typing_extensions.overload", "typing_extensions.override", "typing_extensions.type_check_only",
      "functools.singledispatchmethod",
      "pydantic.functional_validators.field_validator", "pydantic.functional_validators.model_validator", "pydantic.class_validators.validator",
      "django.views.decorators.cache.cache_page",
      "tenacity.retry"
      );


    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    /**
     * Marks decorators that are applied before a transforming decorator and might be affected by this.
     * A transforming decorator is one that returns other than a function.
     * A decorator might be affected if it is not excluded explicitly here.
     *
     * <pre>
     * \@mydeco # < -- warning here
     * \@classmethod
     * def foo(cls):
     *    ...
     * </pre>
     */
    @Override
    public void visitPyFunction(final @NotNull PyFunction node) {
      PyDecoratorList decolist = node.getDecoratorList();
      if (decolist == null) {
        return;
      }
      PyDecorator[] decos = decolist.getDecorators();
      if (decos.length < 2) {
        return;
      }

      for (int i = decos.length - 1; i >= 1; i -= 1) { // start at the innermost
        PyDecorator decoInner = decos[i];
        String decoInnerName = decoInner.getName();
        boolean isTransforming = decoInnerName != null && TRANSFORMING_DECORATORS.contains(decoInnerName) && decoInner.isBuiltin();
        if (!isTransforming) {
          continue;
        }
        for (int j = i - 1; j >= 0; j -= 1) {
          PyDecorator decoOuter = decos[j];
          boolean maybeAffected = !isUnaffectedDecorator(decoOuter);
          if (maybeAffected) {
            registerProblem(
              decoOuter,
              PyPsiBundle.message("INSP.decorator.receives.unexpected.builtin", decoInnerName),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, new RemoveDecoratorQuickFix()
            );
            return;
          }
        }
      }
    }

    private boolean isUnaffectedDecorator(@NotNull PyDecorator decorator) {
      List<@NotNull PyCallable> pyCallables = decorator.multiResolveCalleeFunction(getResolveContext());
      for (PyCallable callable : pyCallables) {
        String decoOuterName = getQualifiedName(callable);
        if (decoOuterName != null && UNAFFECTED_DECORATORS.contains(decoOuterName)) {
          return true;
        }
      }
      return false;
    }

    private static @Nullable String getQualifiedName(PyCallable callable) {
      String decoOuterName = null;
      if (callable instanceof PyFunction pyFunction) {
        PyClass constrClass = PyUtil.turnConstructorIntoClass(pyFunction);
        if (constrClass != null) {
          decoOuterName = constrClass.getQualifiedName();
        }
      }
      if (decoOuterName == null) {
        decoOuterName = callable.getQualifiedName();
      }
      return decoOuterName;
    }
  }
}
