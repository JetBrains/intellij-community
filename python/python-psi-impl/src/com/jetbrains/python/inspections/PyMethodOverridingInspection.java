// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Ref;
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
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterListTypeImpl;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyOverloadType;
import com.jetbrains.python.psi.types.PyParamSpecType;
import com.jetbrains.python.psi.types.PySelfType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
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

      boolean functionOverloaded = isOverloaded(function);

      PyTypeChecker.GenericSubstitutions substitutions = collectAncestorSubstitutions(containingClass);
      for (PsiElement psiElement : superMethods) {
        if (psiElement instanceof PyFunction baseMethod) {
          if (functionOverloaded || isOverloaded(baseMethod)) {
            validateOverriddenOverloadGroup(function, baseMethod, containingClass, substitutions);
          }
          else {
            validateOverriddenFunction(function, baseMethod, containingClass, substitutions);
          }
        }
      }
    }

    /**
     * An {@code @overload} declaration is not a method on its own. It is a part of the signature that callers see.
     * The check validates the whole group once, at the implementation.
     * When the group has no implementation, the first {@code @overload} declaration anchors it.
     * This is the case in a stub file, and in a group with no implementation in a {@code .py} file.
     */
    private boolean anchorsOverloadGroup(@NotNull PyFunction overloadDeclaration) {
      return PyiUtil.getImplementation(overloadDeclaration, myTypeEvalContext) == null &&
             ContainerUtil.getFirstItem(PyiUtil.getOverloads(overloadDeclaration, myTypeEvalContext)) == overloadDeclaration;
    }

    private boolean skipFunctionValidation(@NotNull PyFunction function) {
      // Constructors are not subject to the Liskov substitution principle, so they are normally exempt from override
      // compatibility checks. An explicit `@override` decorator opts them in, see https://github.com/python/typing/issues/2222.
      return (PyUtil.isConstructorLikeMethod(function) && !PyKnownDecoratorUtil.hasOverrideDecorator(function, myTypeEvalContext)) ||
             // An individual @overload declaration is not required to be assignable to the whole signature of the overridden method.
             // The whole overload group is compared once, see anchorsOverloadGroup().
             (PyKnownDecoratorUtil.hasRedeclarationDecorator(function, myTypeEvalContext) && !anchorsOverloadGroup(function)) ||
             hasIgnoredSignature(function);
    }

    private boolean hasIgnoredSignature(@NotNull PyFunction function) {
      return PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, myTypeEvalContext) ||
             ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                  e -> e.ignoreMethodParameters(function, myTypeEvalContext));
    }

    /**
     * Validates an override when the base method, the overriding method, or both are overloaded.
     * The type of an overloaded method is its {@link PyOverloadType}.
     * Thus the override is compatible when the type of the override matches the type of the base method.
     * When a decorator or an extension makes the signature of one {@code @overload} declaration unknown, the check skips the group.
     */
    private void validateOverriddenOverloadGroup(@NotNull PyFunction override,
                                                 @NotNull PyFunction baseMethod,
                                                 @NotNull PyClass containingClass,
                                                 @Nullable PyTypeChecker.GenericSubstitutions substitutions) {
      if (ContainerUtil.exists(PyiUtil.getOverloads(override, myTypeEvalContext), this::hasIgnoredSignature) ||
          ContainerUtil.exists(PyiUtil.getOverloads(baseMethod, myTypeEvalContext), this::hasIgnoredSignature)) {
        return;
      }

      PyType baseMethodType = substitute(methodType(baseMethod), substitutions);
      PyType overrideType = methodType(override);
      if (PyTypeChecker.match(baseMethodType, overrideType, myTypeEvalContext)) return;

      PyClass baseClass = baseMethod.getContainingClass();
      String methodSignature = containingClass.getName() + "." + override.getName() + "()";
      String baseClassName = baseClass != null ? baseClass.getName() : "";
      CodifiedParam methodSignatureParam = CodifiedParam.ofReference(override, methodSignature);
      Object baseClassParam = baseClass != null ? CodifiedParam.ofReference(baseClass, baseClassName) : baseClassName;
      ProblemMessage msg = PyPsiBundle.problemMessage("INSP.signature.mismatch", methodSignatureParam, baseClassParam);
      registerOverrideMismatch(override.getParameterList(), baseMethodType, overrideType, msg);
    }

    private boolean isOverloaded(@NotNull PyFunction method) {
      return !PyiUtil.getOverloads(method, myTypeEvalContext).isEmpty();
    }

    /**
     * Returns the type of {@code method} that callers see.
     * This is a {@link PyOverloadType} for an overloaded method, and the callable type for other methods.
     */
    private @Nullable PyType methodType(@NotNull PyFunction method) {
      List<PyFunction> overloads = PyiUtil.getOverloads(method, myTypeEvalContext);
      if (overloads.isEmpty()) {
        return declaredType(method);
      }
      List<PyCallableType> signatures = new ArrayList<>();
      for (PyFunction overload : overloads) {
        if (declaredType(overload) instanceof PyCallableType signature) {
          signatures.add(signature);
        }
      }
      Ref<PyType> implementation = PyiUtil.isOverload(method, myTypeEvalContext) ? null : Ref.create(myTypeEvalContext.getType(method));
      return new PyOverloadType(signatures, implementation);
    }

    /**
     * Returns the type of {@code function} with an unknown return type when {@code function} has no return annotation.
     * {@link #validateOverriddenFunction} compares the return types only when both methods declare them, and this check does the same.
     */
    private @Nullable PyType declaredType(@NotNull PyFunction function) {
      PyType type = myTypeEvalContext.getType(function);
      if (function.getAnnotationValue() != null || !(type instanceof PyCallableType callableType)) return type;
      List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
      if (parameters == null) return type;
      return new PyCallableTypeImpl(callableType.getTypeParameters(myTypeEvalContext), new PyCallableParameterListTypeImpl(parameters),
                                    PyAnyType.getUnknown(), callableType.getCallable(), callableType.getModifier());
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
