package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This class allows inferring types of function calls based on types without using real PSI
 * Use it in cases when a real PSI call does not exist in code
 */
public final class PySyntheticCallHelper {

  private PySyntheticCallHelper() { }

  /**
   * Infers the call type for a given function, receiver type, argument types, and context.
   *
   * @param function      The function to determine the call type for.
   * @param receiverType  The type of the receiver. Can be null (e.g., top-level function).
   * @param argumentTypes A list of types for the arguments passed to the function (in the same order as in ordinary call).
   * @param context       TypeEvalContext
   * @return The resulting type of the function call.
   */
  public static PyType getCallType(@NotNull PyFunction function,
                                   @Nullable PyType receiverType,
                                   @NotNull List<PyType> argumentTypes,
                                   @NotNull TypeEvalContext context) {
    return getCallTypeOnTypesOnly(function, receiverType, argumentTypes, context);
  }


  /**
   * Infers the call type by the function name, receiver type, argument types, and context.
   *
   * @param functionName  The name of the function to resolve.
   * @param receiverType  The type of the receiver in which the function should be resolved
   * @param argumentTypes A list of types for the arguments passed to the function (in the same order as in ordinary call).
   * @param context       TypeEvalContext
   * @return The resulting type of the function call or null.
   */
  public static @Nullable PyType getCallTypeByFunctionName(@NotNull String functionName,
                                                           @NotNull PyType receiverType,
                                                           @NotNull List<PyType> argumentTypes,
                                                           @NotNull TypeEvalContext context) {
    List<PyFunction> functions = resolveFunctionsByArgumentTypes(functionName, argumentTypes, receiverType, context);
    if (functions.isEmpty()) return null;
    return StreamEx.of(functions)
      .nonNull()
      .map(function -> getCallTypeOnTypesOnly(function, receiverType, argumentTypes, context))
      .collect(PyTypeUtil.toUnion());
  }

  private static @Nullable PyType getCallTypeOnTypesOnly(@NotNull PyFunction function,
                                                         @Nullable PyType receiverType,
                                                         @NotNull List<PyType> arguments,
                                                         @NotNull TypeEvalContext context) {
    PyType type = context.getType(function);
    if (type instanceof PyFunctionType functionType) {
      SyntheticCallArgumentsMapping argumentsMapping = mapArgumentsOnTypes(arguments, functionType, context);
      Map<Ref<PyType>, PyCallableParameter> actualParameters = argumentsMapping.getMappedParameters();
      List<PyCallableParameter> allParameters = ContainerUtil.notNullize(function.getParameters(context));
      PyType returnType = functionType.getReturnType(context);
      return analyzeCallTypeOnTypesOnly(returnType, actualParameters, allParameters, receiverType, context);
    }
    return null;
  }


  public static @NotNull List<PyFunction> resolveFunctionsByArgumentTypes(@NotNull String functionName,
                                                                           @NotNull List<PyType> argumentTypes,
                                                                           @Nullable PyType receiverType,
                                                                           @NotNull TypeEvalContext context) {
    return matchOverloadsByArgumentTypes(resolveFunctionsByName(functionName, receiverType, context),
                                         argumentTypes, receiverType, context);
  }

  private static @NotNull List<PyFunction> resolveFunctionsByName(@NotNull String functionName,
                                                                  @Nullable PyType receiverType,
                                                                  @NotNull TypeEvalContext context) {
    PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    List<? extends RatedResolveResult> members = Collections.emptyList();
    if (receiverType instanceof PyCallableType) {
      members = receiverType.resolveMember(functionName, null, AccessDirection.READ,
                                           resolveContext);
    }
    if (members == null || members.isEmpty()) return Collections.emptyList();

    List<PyFunction> resolvedFunctions =
      StreamEx.of(members)
        .map(RatedResolveResult::getElement)
        .select(PyFunction.class)
        .toList();

    if (resolvedFunctions.isEmpty()) return Collections.emptyList();
    return resolvedFunctions;
  }

  private static @NotNull List<PyFunction> matchOverloadsByArgumentTypes(@NotNull List<PyFunction> functions,
                                                                         @NotNull List<PyType> arguments,
                                                                         @Nullable PyType receiverType,
                                                                         @NotNull TypeEvalContext context) {
    PyFunction firstFunc = ContainerUtil.getFirstItem(functions);
    if (firstFunc != null && PyiUtil.isOverload(firstFunc, context)) {
      List<PyFunction> matchingOverloads = ContainerUtil.filter(
        functions,
        function -> matchesByArgumentTypesOnTypesOnly(function, receiverType, arguments, context)
      );
      if (matchingOverloads.isEmpty()) {
        return Collections.emptyList();
      }
      if (matchingOverloads.size() > 1) {
        boolean someArgumentsHaveUnknownType = ContainerUtil.exists(arguments, arg -> arg == null);
        if (someArgumentsHaveUnknownType) {
          return matchingOverloads;
        }
      }
      return List.of(matchingOverloads.get(0));
    }
    return functions;
  }

  private static @Nullable PyType analyzeCallTypeOnTypesOnly(@Nullable PyType type,
                                                             @NotNull Map<Ref<PyType>, PyCallableParameter> actualParameters,
                                                             @NotNull Collection<PyCallableParameter> allParameters,
                                                             @Nullable PyType receiverType,
                                                             @NotNull TypeEvalContext context) {
    GenericSubstitutions substitutions = PyTypeChecker.unifyGenericCallOnArgumentTypes(receiverType, actualParameters, context);
    GenericSubstitutions substitutionsWithUnresolvedReturnGenerics =
      PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics(allParameters, type, substitutions, context);
    return PyTypeChecker.substitute(type, substitutionsWithUnresolvedReturnGenerics, context);
  }

  private static @NotNull SyntheticCallArgumentsMapping mapArgumentsOnTypes(@NotNull List<PyType> arguments,
                                                                            @NotNull PyFunctionType functionType,
                                                                            @NotNull TypeEvalContext context) {
    List<PyCallableParameter> parameters = functionType.getParameters(context);
    if (parameters == null) return SyntheticCallArgumentsMapping.empty(functionType);

    int safeImplicitOffset = Math.min(functionType.getImplicitOffset(), parameters.size());
    List<PyCallableParameter> explicitParameters = parameters.subList(safeImplicitOffset, parameters.size());
    List<PyCallableParameter> implicitParameters = parameters.subList(0, safeImplicitOffset);
    List<PyType> unmappedArguments = arguments.subList(Math.min(explicitParameters.size(), arguments.size()), arguments.size());

    Map<Ref<PyType>, PyCallableParameter> mappedParams = new HashMap<>();

    for (int i = 0; i < explicitParameters.size(); i++) {
      mappedParams.put(Ref.create(i < arguments.size() ? arguments.get(i) : null), explicitParameters.get(i));
    }

    return new SyntheticCallArgumentsMapping(functionType, implicitParameters, mappedParams, unmappedArguments);
  }

  private static boolean matchesByArgumentTypesOnTypesOnly(@NotNull PyFunction callable,
                                                           @Nullable PyType receiverType,
                                                           @NotNull List<PyType> arguments,
                                                           @NotNull TypeEvalContext context) {
    PyType functionType = context.getType(callable);
    if (!(functionType instanceof PyFunctionType)) {
      return false;
    }
    SyntheticCallArgumentsMapping fullMapping = mapArgumentsOnTypes(arguments, (PyFunctionType)functionType, context);

    if (!fullMapping.getUnmappedArguments().isEmpty()) return false;

    Map<Ref<PyType>, PyCallableParameter> allMappedParameters = new LinkedHashMap<>();
    PyCallableParameter firstImplicit = ContainerUtil.getFirstItem(fullMapping.getImplicitParameters());
    if (firstImplicit != null) {
      allMappedParameters.put(Ref.create(receiverType), firstImplicit);
    }
    allMappedParameters.putAll(fullMapping.getMappedParameters());

    return PyTypeChecker.unifyGenericCallOnArgumentTypes(receiverType, allMappedParameters, context) != null;
  }

  private static class SyntheticCallArgumentsMapping {
    @Nullable private final PyCallableType myReceiverType;
    @NotNull private final List<PyCallableParameter> myImplicitParameters;
    @NotNull private final List<PyType> myUnmappedArguments;
    @NotNull private final Map<Ref<PyType>, PyCallableParameter> myMappedParameters;

    SyntheticCallArgumentsMapping(@Nullable PyCallableType receiverType,
                                  @NotNull List<PyCallableParameter> implicitParameters,
                                  @NotNull Map<Ref<PyType>, PyCallableParameter> mappedParameters,
                                  @NotNull List<PyType> unmappedArguments) {
      myReceiverType = receiverType;
      myImplicitParameters = implicitParameters;
      myMappedParameters = mappedParameters;
      myUnmappedArguments = unmappedArguments;
    }

    @NotNull List<PyCallableParameter> getImplicitParameters() {
      return myImplicitParameters;
    }

    @NotNull Map<Ref<PyType>, PyCallableParameter> getMappedParameters() {
      return myMappedParameters;
    }

    private @NotNull List<PyType> getUnmappedArguments() {
      return myUnmappedArguments;
    }

    @Nullable PyCallableType getReceiverType() {
      return myReceiverType;
    }

    @NotNull
    public static SyntheticCallArgumentsMapping empty(@NotNull PyCallableType receiverType) {
      return new SyntheticCallArgumentsMapping(receiverType, Collections.emptyList(), Collections.emptyMap(), Collections.emptyList());
    }
  }
}
