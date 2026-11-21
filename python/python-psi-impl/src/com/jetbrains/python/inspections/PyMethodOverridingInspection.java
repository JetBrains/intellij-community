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
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterListTypeImpl;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyParamSpecType;
import com.jetbrains.python.psi.types.PySelfType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class PyMethodOverridingInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    TypeEvalContext context = PyInspectionVisitor.getContext(session);
    if (context.getUsesExternalTypeEngine()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new Visitor(holder, context);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(final @NotNull PyFunction function) {
      PyClass containingClass = function.getContainingClass();

      if (containingClass == null || skipFunctionValidation(function)) return;

      Collection<PsiElement> superMethods = PySuperMethodsSearch.search(function, myTypeEvalContext).findAll();
      if (superMethods.isEmpty()) return;

      PyTypeChecker.GenericSubstitutions substitutions = collectAncestorSubstitutions(containingClass);
      for (PsiElement psiElement : superMethods) {
        if (psiElement instanceof PyFunction baseMethod) {
          validateOverriddenFunction(function, baseMethod, containingClass, substitutions);
        }
      }
    }

    private boolean skipFunctionValidation(@NotNull PyFunction function) {
      // Constructors are not subject to the Liskov substitution principle, so they are normally exempt from override
      // compatibility checks. An explicit `@override` decorator opts them in, see https://github.com/python/typing/issues/2222.
      return (PyUtil.isConstructorLikeMethod(function) && !PyKnownDecoratorUtil.hasOverrideDecorator(function, myTypeEvalContext)) ||
             // Only the implementation of an overloaded method is compared with the base method: an individual @overload
             // declaration is not required to be assignable to the whole signature of the overridden method.
             PyKnownDecoratorUtil.hasRedeclarationDecorator(function, myTypeEvalContext) ||
             PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, myTypeEvalContext) ||
             ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                  e -> e.ignoreMethodParameters(function, myTypeEvalContext));
    }

    private void validateOverriddenFunction(@NotNull PyFunction function,
                                            @NotNull PyFunction baseMethod,
                                            @NotNull PyClass containingClass,
                                            @Nullable PyTypeChecker.GenericSubstitutions substitutions) {
      PyClass baseClass = baseMethod.getContainingClass();
      String methodSignature = containingClass.getName() + "." + function.getName() + "()";
      String baseClassName = baseClass != null ? baseClass.getName() : "";

      // The method signature names `function`; the base class name names `baseClass` (when present).
      CodifiedParam methodSignatureParam = CodifiedParam.ofReference(function, methodSignature);
      Object baseClassParam = baseClass != null ? CodifiedParam.ofReference(baseClass, baseClassName) : baseClassName;

      List<PyCallableParameter> baseMethodParameters = baseMethod.getParameters(myTypeEvalContext);
      List<PyCallableParameter> functionParameters = function.getParameters(myTypeEvalContext);

      // We only inspect `self`/`cls` if Base declares an explicit receiver contract.
      // see com.jetbrains.python.PyMethodOverridingInspectionTest#testExplicitReceiverDomainNotHonored
      PyCallableParameter baseReceiver = ContainerUtil.getFirstItem(baseMethodParameters);
      boolean baseReceiverIsFixed = baseReceiver != null && baseReceiver.isSelf() &&
                                    !(baseReceiver.getType(myTypeEvalContext) instanceof PySelfType);
      if (!baseReceiverIsFixed) {
        baseMethodParameters = ParamHelper.dropSelf(baseMethodParameters);
        functionParameters = ParamHelper.dropSelf(functionParameters);
      }

      baseMethodParameters = substituteParameters(baseMethodParameters, substitutions);

      PyCallableParameterListTypeImpl baseMethodInputSignature = new PyCallableParameterListTypeImpl(baseMethodParameters);
      PyCallableParameterListTypeImpl functionInputSignature = new PyCallableParameterListTypeImpl(functionParameters);

      if (!PyTypeChecker.match(baseMethodInputSignature, functionInputSignature, myTypeEvalContext)) {
        ProblemMessage msg = PyPsiBundle.problemMessage("INSP.signature.mismatch", methodSignatureParam, baseClassParam);
        LocalQuickFix fix = PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchingMethods(function, baseMethod);

        // Keep the headline's clickable `B.f()`/`A` links: feed the rich tooltip HTML, not the escaped description.
        String diff = PyTypeDiff.paramsDiffTooltip(baseMethodParameters, functionParameters,
                                                   PyInspectionMessages.tooltipHeadline(msg), myTypeEvalContext,
                                                   baseMethod.getTypeParameterList(), function.getTypeParameterList());
        ProblemMessage problemMessage = new ProblemMessage(msg.description(), diff);
        // The diff is the message tooltip; registerOverrideMismatch appends the breakdown below it on-the-fly.
        if (fix != null) {
          registerOverrideMismatch(function.getParameterList(), baseMethodInputSignature, functionInputSignature, problemMessage, fix);
        }
        else {
          registerOverrideMismatch(function.getParameterList(), baseMethodInputSignature, functionInputSignature, problemMessage);
        }
      }

      if (function.isAsync() != baseMethod.isAsync() && isAsyncMismatchComparable(function, baseMethod)) {
        PyType baseMethodReturnType = substitute(myTypeEvalContext.getReturnType(baseMethod), substitutions);
        PyType overriddenReturnType = myTypeEvalContext.getReturnType(function);
        // A function without `async` can still return a coroutine, and a coroutine is an awaitable.
        if (!PyTypeChecker.match(baseMethodReturnType, overriddenReturnType, myTypeEvalContext)) {
          String key = function.isAsync() ? "INSP.overriding.method.adds.async" : "INSP.overriding.method.removes.async";
          ProblemMessage msg = PyPsiBundle.problemMessage(key, methodSignatureParam, baseClassParam);
          registerOverrideMismatch(function.getNameIdentifier(), baseMethodReturnType, overriddenReturnType, msg);
          return;
        }
      }

      PyAnnotation annotation = function.getAnnotation();
      PyExpression returnExpression = annotation != null ? annotation.getValue() : null;
      String baseMethodAnnotation = baseMethod.getAnnotationValue();

      if (returnExpression == null || baseMethodAnnotation == null) return;

      PyType baseMethodReturnType = substitute(myTypeEvalContext.getReturnType(baseMethod), substitutions);
      PyType overriddenReturnType = myTypeEvalContext.getReturnType(function);

      if (!PyTypeChecker.match(baseMethodReturnType, overriddenReturnType, myTypeEvalContext)) {
        ProblemMessage msg = PyPsiBundle.problemMessage("INSP.overridden.method.return.type.mismatch", methodSignatureParam, baseClassParam);
        registerOverrideMismatch(returnExpression, baseMethodReturnType, overriddenReturnType, msg);
      }
    }

    /**
     * Checks that the return types of {@code function} and {@code baseMethod} show what {@code async} changes.
     * An async generator returns no coroutine. A base method that only raises {@code NotImplementedError} is a placeholder,
     * and a decorator of the base method can change its return type.
     */
    private boolean isAsyncMismatchComparable(@NotNull PyFunction function, @NotNull PyFunction baseMethod) {
      return !function.isGenerator() && !baseMethod.isGenerator() &&
             !baseMethod.onlyRaisesNotImplementedError() &&
             !PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(baseMethod, myTypeEvalContext);
    }

    /**
     * Returns the substitutions of the type parameters of the ancestors of {@code containingClass} with the type arguments
     * that {@code containingClass} gives them. The own type parameters of a generic class map to themselves.
     * Returns {@code null} when no ancestor is generic.
     */
    private @Nullable PyTypeChecker.GenericSubstitutions collectAncestorSubstitutions(@NotNull PyClass containingClass) {
      PyClassType classType = PyTypeChecker.findGenericDefinitionType(containingClass, myTypeEvalContext);
      if (classType == null && myTypeEvalContext.getType(containingClass) instanceof PyClassType plainClassType) {
        classType = plainClassType;
      }
      if (classType == null) return null;
      PyTypeChecker.GenericSubstitutions substitutions =
        PyTypeChecker.INSTANCE.collectTypeSubstitutions(classType.toInstance(), myTypeEvalContext);
      return substitutions.getTypeVars().isEmpty() && substitutions.getTypeVarTuples().isEmpty() && substitutions.getParamSpecs().isEmpty()
             ? null
             : substitutions;
    }

    private @Nullable PyType substitute(@Nullable PyType type, @Nullable PyTypeChecker.GenericSubstitutions substitutions) {
      if (substitutions == null || !PyTypeChecker.hasGenerics(type, myTypeEvalContext)) return type;
      return PyTypeChecker.substitute(type, substitutions, myTypeEvalContext);
    }

    /**
     * Replaces {@code *args: P.args, **kwargs: P.kwargs} with the parameters of {@code P}.
     * Otherwise, substitutes the type of each parameter separately, so that a parameter such as {@code *args: *Ts} stays one parameter.
     */
    private @NotNull List<PyCallableParameter> substituteParameters(@NotNull List<PyCallableParameter> parameters,
                                                                    @Nullable PyTypeChecker.GenericSubstitutions substitutions) {
      if (substitutions == null) return parameters;
      if ((ParamHelper.isArgsKwargsSignature(parameters) || ParamHelper.isSelfArgsKwargsSignature(parameters)) &&
          ContainerUtil.getLastItem(parameters).getType(myTypeEvalContext) instanceof PyParamSpecType &&
          substitute(new PyCallableParameterListTypeImpl(parameters), substitutions) instanceof PyCallableParameterListType paramSpecParameters) {
        return paramSpecParameters.getParameters();
      }
      return ContainerUtil.map(parameters, parameter -> {
        PyParameter psi = parameter.getParameter();
        if (psi == null) return parameter;
        PyType type = parameter.getType(myTypeEvalContext);
        PyType substituted = substitute(type, substitutions);
        return substituted == type ? parameter : PyCallableParameterImpl.psi(psi, substituted);
      });
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
