// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyCallableParameterListTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyMethodOverridingInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    TypeEvalContext context = PyInspectionVisitor.getContext(session);
    Visitor visitor = new Visitor(holder, context);
    visitor.downgradeHighlightForTypeEngine = context.getUsesExternalTypeEngine();
    return visitor;
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(final @NotNull PyFunction function) {
      PyClass containingClass = function.getContainingClass();

      if (containingClass == null || skipFunctionValidation(function)) return;

      for (PsiElement psiElement : PySuperMethodsSearch.search(function, myTypeEvalContext).findAll()) {
        if (psiElement instanceof PyFunction baseMethod) {
          validateOverriddenFunction(function, baseMethod, containingClass);
        }
      }
    }

    private boolean skipFunctionValidation(@NotNull PyFunction function) {
      return PyUtil.isConstructorLikeMethod(function) ||
             PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, myTypeEvalContext) ||
             ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                  e -> e.ignoreMethodParameters(function, myTypeEvalContext));
    }

    private void validateOverriddenFunction(@NotNull PyFunction function,
                                            @NotNull PyFunction baseMethod,
                                            @NotNull PyClass containingClass) {
      PyClass baseClass = baseMethod.getContainingClass();
      String methodSignature = containingClass.getName() + "." + function.getName() + "()";
      String baseClassName = baseClass != null ? baseClass.getName() : "";

      // The method signature names `function`; the base class name names `baseClass` (when present).
      CodifiedParam methodSignatureParam = CodifiedParam.ofReference(function, methodSignature);
      Object baseClassParam = baseClass != null ? CodifiedParam.ofReference(baseClass, baseClassName) : baseClassName;

      PyCallableParameterListTypeImpl baseMethodInputSignature =
        new PyCallableParameterListTypeImpl(baseMethod.getParameters(myTypeEvalContext));
      PyCallableParameterListTypeImpl functionInputSignature =
        new PyCallableParameterListTypeImpl(function.getParameters(myTypeEvalContext));

      if (!PyTypeChecker.match(baseMethodInputSignature, functionInputSignature, myTypeEvalContext)) {
        ProblemMessage msg = PyPsiBundle.problemMessage("INSP.signature.mismatch", methodSignatureParam, baseClassParam);
        LocalQuickFix fix = PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchingMethods(function, baseMethod);

        // Keep the headline's clickable `B.f()`/`A` links: feed the rich tooltip HTML, not the escaped description.
        String diff = PyTypeDiff.paramsDiffTooltip(baseMethod.getParameters(myTypeEvalContext),
                                                   function.getParameters(myTypeEvalContext),
                                                   PyInspectionMessages.tooltipHeadline(msg), myTypeEvalContext);
        ProblemMessage problemMessage = new ProblemMessage(msg.description(), diff);
        // The diff is the message tooltip; registerOverrideMismatch appends the breakdown below it on-the-fly.
        if (fix != null) {
          registerOverrideMismatch(function.getParameterList(), baseMethodInputSignature, functionInputSignature, problemMessage, fix);
        }
        else {
          registerOverrideMismatch(function.getParameterList(), baseMethodInputSignature, functionInputSignature, problemMessage);
        }
      }

      PyAnnotation annotation = function.getAnnotation();
      PyExpression returnExpression = annotation != null ? annotation.getValue() : null;
      String baseMethodAnnotation = baseMethod.getAnnotationValue();

      if (returnExpression == null || baseMethodAnnotation == null) return;

      PyType baseMethodReturnType = myTypeEvalContext.getReturnType(baseMethod);
      PyType overriddenReturnType = myTypeEvalContext.getReturnType(function);

      if (!PyTypeChecker.match(baseMethodReturnType, overriddenReturnType, myTypeEvalContext)) {
        ProblemMessage msg = PyPsiBundle.problemMessage("INSP.overridden.method.return.type.mismatch", methodSignatureParam, baseClassParam);
        registerOverrideMismatch(returnExpression, baseMethodReturnType, overriddenReturnType, msg);
      }
    }

    /**
     * Registers an override-incompatibility problem, attaching the breakdown ({@link PyTypeChecker#explainMismatch})
     * as a separate on-the-fly HTML tooltip while keeping the one-line {@code message} as the flat description.
     * Falls back to a plain problem when not on-the-fly or when the failure category is not instrumented.
     */
    private void registerOverrideMismatch(@Nullable PsiElement element,
                                          @Nullable PyType expected,
                                          @Nullable PyType actual,
                                          @NotNull ProblemMessage message,
                                          LocalQuickFix @NotNull ... fixes) {
      registerProblemWithTooltip(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 fixes,
                                 () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(message, expected, actual, myTypeEvalContext, element));
    }
  }
}
