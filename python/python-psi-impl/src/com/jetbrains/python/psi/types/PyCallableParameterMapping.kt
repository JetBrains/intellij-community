package com.jetbrains.python.psi.types

import com.jetbrains.python.isPrivate
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Matches signatures of callables according to the Callable compatibility rules
 * @see <a href="https://typing.python.org/en/latest/spec/callables.html#assignability-rules-for-callables">Specification</a>
 */
@ApiStatus.Internal
object PyCallableParameterMapping {

  private enum class ParameterKind {
    POSITIONAL_ONLY,
    POSITIONAL_OR_KEYWORD,
    KEYWORD_ONLY,
    POSITIONAL_CONTAINER,
    KEYWORD_CONTAINER,
    TYPE_VAR_TUPLE;
  }

  private data class Parameter(
    val parameter: PyCallableParameter,
    val kind: ParameterKind,
  ) {
    val name: String? get() = parameter.name
    val hasDefault: Boolean get() = parameter.hasDefaultValue()

    fun getArgumentType(context: TypeEvalContext): PyType? = parameter.getArgumentType(context)

    val acceptsPositionalArgument =
      kind == ParameterKind.POSITIONAL_ONLY || kind == ParameterKind.POSITIONAL_OR_KEYWORD || kind == ParameterKind.POSITIONAL_CONTAINER

    val acceptsKeywordArgument =
      kind == ParameterKind.KEYWORD_ONLY || kind == ParameterKind.POSITIONAL_OR_KEYWORD || kind == ParameterKind.KEYWORD_CONTAINER
  }

  /**
   * Maps a list of expected parameters to a list of actual parameters for a Callable
   * by analyzing their signatures and attempting to align them.
   *
   * @param expectedCallableParameters a list of callable parameters from the expected Callable.
   * @param actualCallableParameters a list of callable parameters from the actual Callable.
   * @param context TypeEvalContext.
   * @return a `PyCallableParameterMapping` object representing the result of the parameter mapping,
   *         or `null` if one of the signatures is specified incorrectly, or they do not match by structure
   */
  @JvmStatic
  fun mapCallableParameters(
    expectedCallableParameters: List<PyCallableParameter>,
    actualCallableParameters: List<PyCallableParameter>,
    context: TypeEvalContext,
  ): PyTypeParameterMapping? {
    val expectedCategorizedParameters = categorizeParameters(expectedCallableParameters, context) ?: return null
    val actualCategorizedParameters = categorizeParameters(actualCallableParameters, context) ?: return null

    val expectedParameters = ArrayDeque(expectedCategorizedParameters)
    val actualParameters = ArrayDeque(actualCategorizedParameters)

    val expectedTypes = mutableListOf<PyType?>()
    val actualTypes = mutableListOf<PyType?>()

    val actualKeywordContainer = actualParameters.firstOrNull { it.kind == ParameterKind.KEYWORD_CONTAINER }

    val actualHasTypeVarTuple = actualParameters.any { it.kind == ParameterKind.TYPE_VAR_TUPLE }

    val actualKeywordOrPositional = actualParameters
      .filter {
        (it.kind == ParameterKind.POSITIONAL_OR_KEYWORD ||
         it.kind == ParameterKind.KEYWORD_ONLY) && it.name != null
      }
      .associateBy { it.name!! }
      .toMutableMap()

    /**
     * if Callable contains TypeVarTuple, say, Callable[[int, str, *Ts, bool, int], None]
     * we need to calculate how many *positional-only* parameters
     * (positional including positional container in case of real signature)
     * are expected from the right side of the TypeVarTuple.
     */
    val actualPositionalOrContainerCount = actualParameters.count { it.acceptsPositionalArgument }
    val expectedPositionalOrContainerCount = expectedParameters.count { it.acceptsPositionalArgument }

    val paramsTypeVarTupleShouldAccept =
      actualPositionalOrContainerCount - expectedPositionalOrContainerCount + (if (actualHasTypeVarTuple) 1 else 0)

    while (expectedParameters.isNotEmpty() && actualParameters.isNotEmpty()) {
      val expectedParameter = expectedParameters.peek()
      val actualParameter = actualParameters.peek()

      when (expectedParameter.kind) {
        // Positional-only can match with positional or positional-or-keyword parameters
        ParameterKind.POSITIONAL_ONLY -> {
          if (actualParameter.parameter.isPositionalContainer) {
            expectedTypes.add(expectedParameter.getArgumentType(context))
            actualTypes.add(actualParameter.getArgumentType(context))
            expectedParameters.pop()
          }
          else if (actualParameter.acceptsPositionalArgument) {
            if (expectedParameter.hasDefault && !actualParameter.hasDefault) {
              return null
            }
            expectedTypes.add(expectedParameter.getArgumentType(context))
            expectedParameters.pop()
            actualTypes.add(actualParameter.getArgumentType(context))
            actualParameters.pop()
          }
          else {
            return null
          }
        }
        // Must match by name
        ParameterKind.POSITIONAL_OR_KEYWORD -> {
          if (actualParameter.kind == ParameterKind.POSITIONAL_OR_KEYWORD) {
            when {
              expectedParameter.parameter.isSelf && actualParameter.parameter.isSelf -> continue
              expectedParameter.name != actualParameter.name -> return null
              expectedParameter.hasDefault && !actualParameter.hasDefault -> return null
            }
            expectedTypes.add(expectedParameter.getArgumentType(context))
            expectedParameters.pop()
            actualTypes.add(actualParameter.getArgumentType(context))
            actualParameters.pop()
          }
          else if (actualParameter.kind == ParameterKind.POSITIONAL_CONTAINER && actualKeywordContainer != null) {
            expectedTypes.add(expectedParameter.getArgumentType(context))
            actualTypes.add(actualParameter.getArgumentType(context))
            expectedParameters.pop()
          }
          else {
            return null
          }
        }
        // Keyword-only parameter must match by name from the set of keyword-only or
        // positional-or-keyword parameters from the actual signature
        ParameterKind.KEYWORD_ONLY -> {
          val actualKwOnlyOrPositionalParam = actualKeywordOrPositional.remove(expectedParameter.name)
          if (actualKwOnlyOrPositionalParam != null) {
            if (expectedParameter.hasDefault && !actualKwOnlyOrPositionalParam.hasDefault) {
              return null
            }
            expectedTypes.add(expectedParameter.getArgumentType(context))
            expectedParameters.pop()
            actualTypes.add(actualKwOnlyOrPositionalParam.getArgumentType(context))
            require(actualParameters.remove(actualKwOnlyOrPositionalParam))
          }
          else if (actualKeywordContainer != null) {
            expectedTypes.add(expectedParameter.getArgumentType(context))
            actualTypes.add(actualKeywordContainer.getArgumentType(context))
            // All keyword parameters and the corresponding container are consumed, so we can pop the container
            expectedParameters.pop()
          }
          else {
            return null
          }
        }
        // *args can consume multiple positional parameters
        ParameterKind.POSITIONAL_CONTAINER -> {
          val argsType = expectedParameter.getArgumentType(context)
          // Consume all remaining positional parameters
          var actualPositional = actualParameters.pop()
          while (actualParameters.isNotEmpty() && !actualPositional.parameter.isPositionalContainer) {
            if (!actualPositional.acceptsPositionalArgument || !actualPositional.hasDefault) {
              return null
            }
            expectedTypes.add(argsType)
            actualTypes.add(actualPositional.getArgumentType(context))
            actualPositional = actualParameters.pop()
          }
          // we need to match containers themselves as well (e.g. *args: T <- *args: T1)
          if (actualPositional.parameter.isPositionalContainer) {
            // *args: T is not propagated to *tuple[T, ...] here to avoid conflicts with mapping of subsequent types
            expectedTypes.add(argsType)
            actualTypes.add(actualPositional.getArgumentType(context))
            // All positional parameters and the corresponding container are consumed, so we can pop the container
            expectedParameters.pop()
          }
        }
        // **kwargs can consume multiple keyword parameters
        ParameterKind.KEYWORD_CONTAINER -> {
          val kwargsType = expectedParameter.getArgumentType(context)
          // Consume all remaining keyword parameters
          var actualKeywordParam = actualParameters.pop()
          while (actualParameters.isNotEmpty() && !actualKeywordParam.parameter.isKeywordContainer) {
            if (!(actualKeywordParam.acceptsKeywordArgument) || !actualKeywordParam.hasDefault) {
              return null
            }
            expectedTypes.add(kwargsType)
            actualTypes.add(actualKeywordParam.getArgumentType(context))

            actualKeywordParam = actualParameters.pop()
          }
          // match keyword containers themselves
          if (actualKeywordParam.parameter.isKeywordContainer) {
            expectedTypes.add(kwargsType)
            actualTypes.add(actualKeywordParam.getArgumentType(context))
            expectedParameters.pop()
          }
        }
        // TypeVarTuple (*Ts) consumes a calculated number of positional parameters
        ParameterKind.TYPE_VAR_TUPLE -> {
          expectedTypes.add(expectedParameter.getArgumentType(context))

          repeat(paramsTypeVarTupleShouldAccept) {
            if (actualParameters.isEmpty()) return null
            val actualParameter = actualParameters.pop()
            if (!actualParameter.acceptsPositionalArgument && actualParameter.kind != ParameterKind.TYPE_VAR_TUPLE) {
              return null
            }
            var actualType = actualParameter.getArgumentType(context)
            // *args: T mapped to TypeVarTuple should be represented as *tuple[T, ...]
            if (actualParameter.parameter.isPositionalContainer && actualType !is PyPositionalVariadicType) {
              actualType = PyUnpackedTupleTypeImpl.createUnbound(actualType)
            }
            actualTypes.add(actualType)
          }
          // TypeVarTuple consumed all the required parameters.
          expectedParameters.pop()
        }
      }
    }
    // Post-process remaining params
    while (expectedParameters.isNotEmpty()) {
      val parameter = expectedParameters.pop()
      val type = parameter.getArgumentType(context)
      when {
        // TODO remove these container checks when type parameters in protocols are properly substituted
        parameter.parameter.isPositionalContainer && (type is PyPositionalVariadicType || type is PyParamSpecType) -> continue
        parameter.parameter.isKeywordContainer && type is PyParamSpecType -> continue
        parameter.kind == ParameterKind.TYPE_VAR_TUPLE -> { // can be empty
          if (actualTypes.isEmpty()) {
            expectedTypes.add(type)
          }
        }
        else -> return null
      }
    }
    // Handle unmatched actual parameters (extra in actual)
    while (actualParameters.isNotEmpty()) {
      val parameter = actualParameters.pop()
      when {
        parameter.parameter.isPositionalContainer || parameter.parameter.isKeywordContainer -> continue
        parameter.hasDefault -> continue
        else -> return null
      }
    }

    return PyTypeParameterMapping.mapByShape(expectedTypes, actualTypes)
  }

  // Unwraps *args: *tuple[T, T1] to a sequence of positional parameters: T, T1 for matching purposes
  private fun flattenPositionalContainer(parameters: List<Parameter>, context: TypeEvalContext): List<Parameter> {
    val flattenedParameters = mutableListOf<Parameter>()
    for (parameter in parameters) {
      val parameterType = parameter.getArgumentType(context)
      if (parameter.parameter.isPositionalContainer && parameterType is PyUnpackedTupleType && !parameterType.isUnbound()) {
        val flattenedTypes = parameterType.getElementTypes()
        val unwrappedArgParameters = flattenedTypes.map { type ->
          Parameter(PyCallableParameterImpl.nonPsi(type), ParameterKind.POSITIONAL_ONLY)
        }
        flattenedParameters.addAll(unwrappedArgParameters)
      }
      else {
        flattenedParameters.add(parameter)
      }
    }
    return flattenedParameters
  }

  private enum class ParameterState {
    POSITIONAL_OR_KEYWORD,
    KEYWORD_ONLY,
    POSITIONAL_CONTAINER,
  }

  /**
   * Categorizes parameters into based on Python's parameter syntax.
   * Returns null if the parameter list is invalid (e.g., duplicate *args or **kwargs).
   */
  private fun categorizeParameters(callableParameters: List<PyCallableParameter>, context: TypeEvalContext): List<Parameter>? {
    val parameters = mutableListOf<Parameter>()

    var positionalContainer: Parameter? = null
    var keywordContainer: Parameter? = null
    var typeVarTupleType: PyTypeVarTupleType? = null

    var state = ParameterState.POSITIONAL_OR_KEYWORD
    for (param in callableParameters) {
      val argumentType = param.getArgumentType(context)
      if (param.isPositionOnlySeparator) {
        parameters.replaceAll { p -> p.copy(kind = ParameterKind.POSITIONAL_ONLY) }
        continue
      }
      else if (param.isKeywordContainer) {
        if (keywordContainer != null) return null

        keywordContainer = Parameter(param, ParameterKind.KEYWORD_CONTAINER)
        parameters.add(keywordContainer)
        continue
      }
      else if (param.isPositionalContainer) {
        if (positionalContainer != null) return null

        positionalContainer = Parameter(param, ParameterKind.POSITIONAL_CONTAINER)
        parameters.add(positionalContainer)
        state = ParameterState.POSITIONAL_CONTAINER
        continue
      }
      if (argumentType is PyTypeVarTupleType) {
        if (typeVarTupleType != null) return null // Only one TypeVarTuple is allowed

        typeVarTupleType = argumentType
        parameters.add(Parameter(param, ParameterKind.TYPE_VAR_TUPLE))
        continue
      }
      else if (param.isKeywordOnlySeparator) {
        state = ParameterState.KEYWORD_ONLY
        continue
      }
      else {
        if (state == ParameterState.POSITIONAL_OR_KEYWORD) {
          val paramName = param.name
          val isPositionalOnly = paramName == null || isPrivate(paramName)
          if (isPositionalOnly) {
            parameters.lastOrNull()?.let {
              if (it.kind != ParameterKind.POSITIONAL_ONLY && it.kind != ParameterKind.TYPE_VAR_TUPLE) {
                // Previous parameter should also be positional-only in this case
                return null
              }
            }
            parameters.add(Parameter(param, ParameterKind.POSITIONAL_ONLY))
          }
          else {
            parameters.add(Parameter(param, ParameterKind.POSITIONAL_OR_KEYWORD))
          }
        }
        else {
          val name = param.name
          if (name == null) {
            return null
          }
          parameters.add(Parameter(param, ParameterKind.KEYWORD_ONLY))
        }
      }
    }
    return flattenPositionalContainer(parameters, context)
  }
}