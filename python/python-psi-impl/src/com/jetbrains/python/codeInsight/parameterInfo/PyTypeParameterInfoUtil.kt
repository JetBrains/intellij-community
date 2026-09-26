// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.parameterInfo

import com.intellij.openapi.util.Ref
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.stubs.PyTypingAliasStubType
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeChecker.collectGenerics
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Helpers for the "Parameter Info" popup shown inside the square brackets of a parameterized type, such as
 * `Generator[<caret>]`. Mirrors the resolution performed by `PyTypeHintsInspection` when it validates type arguments.
 */
@ApiStatus.Internal
object PyTypeParameterInfoUtil {

  /**
   * If the operand of [subscription] refers to something that can be parameterized with type arguments
   * (a generic class or a generic type alias), returns the ordered list of its type parameters. Otherwise,
   * returns an empty list.
   */
  @JvmStatic
  fun getTypeParameters(subscription: PySubscriptionExpression, context: TypeEvalContext): List<PyTypeParameterType> {
    val operand = subscription.operand as? PyReferenceExpression ?: return emptyList()
    val resolveContext = PyResolveContext.defaultContext(context)
    val declaration = operand
      .multiFollowAssignmentsChain(resolveContext) { target ->
        when {
          PyTypingTypeProvider.isExplicitTypeAlias(target, context) -> false
          PyTypingAliasStubType.getAssignedValueStubLike(target) is PyReferenceExpression ->
            target.qualifiedName?.let { PyTypingTypeProvider.OPAQUE_NAMES.contains(it) } == false
          else -> false
        }
      }
      .firstNotNullOfOrNull { it.element } ?: return emptyList()

    return when (declaration) {
      is PyClass -> classTypeParameters(declaration, context)
      is PyTypeAliasStatement -> declaration.typeParameterList?.typeParameters
                                   ?.mapNotNull { PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(it, context) }
                                 ?: emptyList()
      is PyTargetExpression -> aliasTypeParameters(subscription, declaration, context)
      else -> emptyList()
    }
  }

  /**
   * Returns the user-visible representations of the type parameters accepted by the operand of [subscription], formatted
   * as `name[: bound][ = default]`. For instance `["_YieldT_co", "_SendT_contra", "_ReturnT_co"]` for `Generator`, or
   * `["T: int = int", "*Ts", "**P"]` when bounds, defaults (PEP 696) and variadics are involved.
   */
  @JvmStatic
  fun getTypeParameterRepresentations(subscription: PySubscriptionExpression, context: TypeEvalContext): List<String> {
    return getTypeParameters(subscription, context).map { representation(it, context) }
  }

  private fun representation(typeParameter: PyTypeParameterType, context: TypeEvalContext): String {
    // The name already carries the leading '*'/'**' for TypeVarTuple/ParamSpec; only plain TypeVars can have a bound.
    val result = StringBuilder(typeParameter.name)
    // An absent bound/default is modeled as `null` or as the `Unknown`/`Any` sentinel depending on the
    // `python.type.any` registry, so skip both.
    val bound = (typeParameter as? PyTypeVarType)?.bound
    if (bound != null && bound !is PyAnyType) {
      result.append(": ").append(PythonDocumentationProvider.getTypeName(bound, context))
    }
    val defaultType = typeParameter.defaultType?.get()
    if (defaultType != null && defaultType !is PyAnyType) {
      result.append(" = ").append(PythonDocumentationProvider.getTypeName(defaultType, context))
    }
    return result.toString()
  }

  private fun classTypeParameters(cls: PyClass, context: TypeEvalContext): List<PyTypeParameterType> {
    return PyTypeChecker.findGenericDefinitionType(cls, context)
             ?.typeArguments
             ?.filterIsInstance<PyTypeParameterType>()
           ?: emptyList()
  }

  private fun aliasTypeParameters(subscription: PySubscriptionExpression,
                                  declaration: PyTargetExpression,
                                  context: TypeEvalContext): List<PyTypeParameterType> {
    val builtinName = declaration.qualifiedName?.let { PyTypingTypeProvider.BUILTIN_COLLECTION_CLASSES[it] }
    if (builtinName != null) {
      val cls = PyBuiltinCache.getInstance(subscription).getClass(builtinName)
      if (cls != null) {
        return classTypeParameters(cls, context)
      }
    }

    val assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(declaration) ?: return emptyList()
    val assignedValueType = Ref.deref(PyTypingTypeProvider.getType(assignedValue, context)) ?: return emptyList()
    // A bare class reference on the RHS (`Alias = GenericClass`) is followed through in `multiFollowAssignmentsChain`,
    // so `declaration` here is always a genuine alias expression rather than a class reference: collect its generics.
    return assignedValueType.collectGenerics(context).allTypeParameters.distinct()
  }
}
