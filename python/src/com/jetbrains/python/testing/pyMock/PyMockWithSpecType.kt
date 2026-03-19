// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import com.jetbrains.python.documentation.PyTypeRenderer
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeMember
import com.jetbrains.python.psi.types.PyTypeVisitor
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType

/**
 * The type of a mock that imitates [specType].
 *
 * A mock gets a spec from `spec=` or `spec_set=`. An attribute of the mock resolves to a member of [mockType]
 * or of [specType]. Other attributes are unresolved, because at runtime they raise `AttributeError`.
 *
 * This type is not a [PyClassType], so checks that need a real class instance do not apply to it.
 * An example is the check for an assignment to a field of a frozen dataclass.
 * [PyMockTypeCheckerExtension] decides where a mock with a spec is assignable.
 *
 * ```python
 * class A:
 *     def foo(self): ...
 *
 * x = MagicMock(spec=A)
 * x.foo()           # OK: defined in A
 * x.assert_called() # OK: defined in Mock
 * x.bar()           # Unresolved: not defined in A or Mock
 * ```
 *
 * @property mockType the instance type of the mock class, for example `MagicMock`
 * @property specType the imitated type. For a mock it is the spec class instance.
 *   For an attribute of a mock it is the type of the spec member.
 * @property kind how the mock got its spec. Every attribute of a [PyMockSpecKind.CHILD] mock is valid.
 */
internal class PyMockWithSpecType(
  val mockType: PyClassType,
  val specType: PyType,
  val kind: PyMockSpecKind = PyMockSpecKind.SPEC,
) : PyClassLikeType {

  override fun isDefinition(): Boolean = false

  override fun toInstance(): PyClassLikeType = this

  override fun toClass(): PyClassLikeType = mockType.toClass()

  override fun getClassQName(): String? = mockType.classQName

  override val declarationElement: PyQualifiedNameOwner?
    get() = mockType.declarationElement

  override fun getSuperClassTypes(context: TypeEvalContext): List<PyClassLikeType?> = listOf(mockType)

  override fun getAncestorTypes(context: TypeEvalContext): List<PyClassLikeType?> {
    val specClass = specType as? PyClassLikeType
    val specAncestors = if (specClass != null) listOf(specClass) + specClass.getAncestorTypes(context) else emptyList()
    return (listOf(mockType) + mockType.getAncestorTypes(context) + specAncestors).distinct()
  }

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> = resolveMember(name, location, direction, resolveContext, true)

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
    inherited: Boolean,
  ): List<RatedResolveResult> {
    val mockResult = mockType.resolveMember(name, location, direction, resolveContext, inherited)
    if (!mockResult.isNullOrEmpty()) return mockResult

    val specResult = when (specType) {
      is PyClassLikeType -> specType.resolveMember(name, location, direction, resolveContext, inherited)
      else -> specType.resolveMember(name, location, direction, resolveContext)
    }
    // An empty list, not null, lets the resolver still look for attributes that the code assigns to the mock.
    return specResult.orEmpty()
  }

  override fun findMember(name: String, resolveContext: PyResolveContext): List<PyTypeMember> =
    mockType.findMember(name, resolveContext).ifEmpty { specType.findMember(name, resolveContext) }

  override fun getAllMembers(resolveContext: PyResolveContext): List<PyTypeMember> =
    mockType.getAllMembers(resolveContext) + specType.getAllMembers(resolveContext)

  override fun visitMembers(processor: Processor<in PsiElement>, inherited: Boolean, context: TypeEvalContext) {
    mockType.visitMembers(processor, inherited, context)
    (specType as? PyClassLikeType)?.visitMembers(processor, inherited, context)
  }

  override fun getMemberNames(inherited: Boolean, context: TypeEvalContext): Set<String> =
    mockType.getMemberNames(inherited, context) + (specType as? PyClassLikeType)?.getMemberNames(inherited, context).orEmpty()

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<Any> =
    (mockType.getCompletionVariants(completionPrefix, location, context).toList() +
     specType.getCompletionVariants(completionPrefix, location, context).toList()).toTypedArray()

  override fun getMetaClassType(context: TypeEvalContext, inherited: Boolean): PyClassLikeType? =
    mockType.getMetaClassType(context, inherited)

  override fun isCallable(): Boolean = mockType.isCallable

  /**
   * The type of the return value of a call on the mock.
   *
   * For an attribute that imitates a spec method, the return value imitates the return type of that method.
   * For other mocks, the return value is a mock without a spec.
   */
  override fun getReturnType(context: TypeEvalContext): PyType? {
    if (!isCallable()) return null
    val specCallable = (specType as? PyCallableType)?.takeIf { it !is PyClassLikeType }
    // A call on an `AsyncMock` returns a coroutine.
    val returnValueMock = getReturnValueMockType(mockType)
                          ?: return specCallable?.getReturnType(context) ?: mockType.getReturnType(context)
    if (specCallable == null) return returnValueMock
    val specReturnType = specCallable.getReturnType(context)
    if (specReturnType !is PyClassType || specReturnType.isNoneType) return returnValueMock
    return PyMockWithSpecType(returnValueMock, specReturnType, PyMockSpecKind.CHILD)
  }

  override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteOwner): PyType? = getReturnType(context)

  override val name: String
    get() = "${mockType.name} (${specType.name ?: "?"})"

  override val isBuiltin: Boolean
    get() = false

  override fun isValid(): Boolean = mockType.isValid && (specType as? PyClassLikeType)?.isValid ?: true

  override fun assertValid(message: String?) {
    mockType.assertValid(message)
    specType.assertValid(message)
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    // A type hint must be a valid expression, so it names only the mock class.
    if (visitor is PyTypeRenderer.TypeHint) return mockType.acceptTypeVisitor(visitor)
    if (visitor is PyTypeRenderer) {
      val mockRender = mockType.acceptTypeVisitor(visitor)
      val specRender = specType.acceptTypeVisitor(visitor)
      if (mockRender != null && specRender != null) {
        @Suppress("UNCHECKED_CAST")
        return HtmlBuilder().append(mockRender).append(" (").append(specRender).append(")").toFragment() as T
      }
    }
    return visitor.visitPyClassLikeType(this)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PyMockWithSpecType) return false
    return mockType == other.mockType && specType == other.specType && kind == other.kind
  }

  override fun hashCode(): Int = 31 * (31 * mockType.hashCode() + specType.hashCode()) + kind.hashCode()

  override fun toString(): String = "PyMockWithSpecType: $name"
}

/**
 * How a [PyMockWithSpecType] got its spec.
 */
internal enum class PyMockSpecKind {
  /** A mock with `spec=` or `spec_set=`. An attribute outside the mock and the spec is unresolved. */
  SPEC,

  /** An attribute or a return value of a [SPEC] mock. CPython creates it without a spec, so every attribute of it is valid. */
  CHILD,
}
