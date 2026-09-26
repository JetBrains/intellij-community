// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCallableArgument
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType

/**
 * Infers types for mock-related constructs:
 *
 * 1. Parameters injected by stacked `@patch` and `@patch.object` decorators:
 *    ```python
 *    @patch("A")   # outermost → last injected param
 *    @patch("B")   # innermost → first injected param
 *    def test(self, mock_b, mock_a): ...
 *    ```
 *    - `mock_b` gets type `MagicMock` (from the innermost `@patch("B")`)
 *    - `mock_a` gets type `MagicMock` (from the outermost `@patch("A")`)
 *    - If `new_callable=SomeClass` is present, the injected parameter has type `SomeClass`.
 *    - Decorators with `new=value` do not inject a parameter.
 *
 * 2. Mock constructor calls with a spec:
 *    ```python
 *    x = MagicMock(spec=MyClass)
 *    ```
 *    The type of `x` is a [PyMockWithSpecType]. It resolves the members of `MagicMock`, such as `assert_called()`,
 *    and the members of `MyClass`. An attribute that imitates a member of `MyClass` is a [PyMockWithSpecType] too.
 */
internal class PyMockTypeProvider : PyTypeProviderBase() {
  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    val qualifier = referenceExpression.qualifier ?: return null
    val memberName = referenceExpression.referencedName ?: return null
    val qualifierType = context.getType(qualifier) as? PyMockWithSpecType ?: return null

    // The default evaluation binds a method only to a class instance, and this type is not one.
    // So a member of the mock class, such as `assert_called`, gets its bound type here.
    val mockMember = getInstanceMember(qualifierType.mockType, memberName, context)
    if (mockMember != null) return mockMember.type

    val specClassType = qualifierType.specType as? PyClassType ?: return null
    val specMember = getInstanceMember(specClassType, memberName, context) ?: return null
    val specMemberType = PyLiteralType.upcastLiteralToClass(specMember.type) ?: return null
    val childMockType = getChildMockType(qualifierType.mockType, specMember.isAsyncFunction)
    return PyMockWithSpecType(childMockType, specMemberType, qualifierType.kind.childKind)
  }

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType?>? {
    if (!context.maySwitchToAST(func)) return null

    val matchedPatch = getInjectingPatchDecorator(param, func, context) ?: return null
    return getTypeForPatch(matchedPatch, func, context)
  }

  override fun prepareCalleeTypeForCall(type: PyType?, callee: PyExpression, context: TypeEvalContext): Ref<PyCallableType?>? {
    // A call on a mock accepts any arguments.
    if (type is PyMockWithSpecType) {
      if (!type.isCallable()) return null
      val returnType = type.getReturnType(context) ?: return null
      return Ref.create(PyCallableTypeImpl.withUnknownParameters(returnType))
    }

    if (type !is PyClassType || !type.isDefinition) return null

    val classFqn = type.pyClass.qualifiedName ?: return null
    if (classFqn !in MOCK_CLASS_FQNS) return null

    // Keep the constructor parameters, so that the arguments of the mock constructor are still checked.
    return Ref.create(MockConstructorType(type.toInstance(), type.getParameters(context)))
  }

  private fun getTypeForPatch(dec: PyDecorator, anchor: PyFunction, context: TypeEvalContext): Ref<PyType?>? {
    // If new_callable= is specified, return an instance of that class
    val newCallableExpr = dec.getKeywordArgument("new_callable")
    if (newCallableExpr != null) {
      val callableType = newCallableExpr.getType(context)
      val classType = callableType as? PyClassType
      if (classType != null && classType.isDefinition) {
        return Ref(classType.toInstance())
      }
    }

    // Default: MagicMock
    return getMagicMockType(anchor)
  }

  private fun getMagicMockType(anchor: PsiElement): Ref<PyType?>? {
    val magicMockClass = PyPsiFacade.getInstance(anchor.project)
                           .createClassByQName("unittest.mock.MagicMock", anchor)
                         ?: return null
    return Ref(PyClassTypeImpl(magicMockClass, false))
  }
}

/**
 * The type of a mock class constructor. The call result imitates the spec that the call site gives.
 *
 * The spec argument is read only for the call result. A callee type must not depend on the arguments,
 * because the type of an argument can depend on the callee parameters.
 */
private class MockConstructorType(
  private val mockType: PyClassType,
  parameters: List<PyCallableParameter>?,
) : PyCallableTypeImpl(parameters, mockType) {
  override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteOwner?, arguments: List<PyCallableArgument>): PyType {
    val call = callSite as? PyCallExpression ?: return mockType
    val (specType, kind) = getSpecType(call, context) ?: return mockType
    return PyMockWithSpecType(mockType, specType, kind)
  }
}

/**
 * Returns the instance type that the mock created by [call] imitates and the kind of the spec,
 * or `null` if the mock has no spec.
 *
 * `spec=None` gives no spec. A list or a tuple spec gives only attribute names, so this function ignores it.
 */
private fun getSpecType(call: PyCallExpression, context: TypeEvalContext): Pair<PyType, PyMockSpecKind>? {
  // `spec` is also the first positional parameter of every mock class.
  val firstPositional = call.arguments.firstOrNull()?.takeIf { it !is PyKeywordArgument && it !is PyStarArgument }
  val wrapsExpr = call.getKeywordArgument("wraps")
  val specExpr = call.getKeywordArgument("spec")
                 ?: firstPositional
                 ?: call.getKeywordArgument("spec_set")
                 ?: wrapsExpr
                 ?: return null
  val specClassType = context.getType(specExpr) as? PyClassType ?: return null
  if (specClassType.isNoneType) return null
  if (!specClassType.isDefinition && isNameList(specClassType, specExpr)) return null
  // A spec gives the shape of an object, so a literal type such as `Literal[1]` becomes its class.
  val specType = PyLiteralType.upcastLiteralToClass(specClassType.toInstance()) ?: return null
  return specType to if (specExpr === wrapsExpr) PyMockSpecKind.WRAPS else PyMockSpecKind.SPEC
}

/** Returns `true` if [type] is exactly `list` or `tuple`. CPython reads such a spec as a list of attribute names. */
private fun isNameList(type: PyClassType, anchor: PsiElement): Boolean {
  val builtins = PyBuiltinCache.getInstance(anchor)
  return type.pyClass == builtins.getClass("list") || type.pyClass == builtins.getClass("tuple")
}

/** A member of a class instance and its bound type. */
private class InstanceMember(val type: PyType?, val isAsyncFunction: Boolean)

/**
 * Returns the member [name] of an instance of [classType] with its type after attribute access, or `null` if there is no member.
 *
 * The type of a property is the type of its getter result. The type of a method is bound to the instance.
 */
private fun getInstanceMember(classType: PyClassType, name: String, context: TypeEvalContext): InstanceMember? {
  val property = classType.pyClass.findProperty(name, true, context)
  if (property != null) return InstanceMember(property.getType(classType, context), false)

  val results = classType.resolveMember(name, null, AccessDirection.READ, PyResolveContext.noProperties(context))
  if (results.isNullOrEmpty()) return null
  val isAsyncFunction = results.any { (it.element as? PyFunction)?.isAsync == true }
  return InstanceMember(PyTypeUtil.getTypeOfBoundMember(classType, results, context), isAsyncFunction)
}
