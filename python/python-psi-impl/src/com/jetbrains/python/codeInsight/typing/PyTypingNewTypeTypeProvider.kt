package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.getStringBasedType
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyTypingNewTypeStubImpl.Companion.create
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.PyTypingNewType
import com.jetbrains.python.psi.types.PyTypingNewTypeFactoryType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyTypingNewTypeTypeProvider : PyTypeProviderBase() {
  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType?>? {
    val newType = getNewTypeForResolvedElement(referenceTarget, context)
    return if (newType != null) Ref.create(PyTypingNewTypeFactoryType(newType)) else null
  }

  override fun prepareCalleeTypeForCall(
    type: PyType?,
    call: PyCallExpression,
    context: TypeEvalContext,
  ): Ref<PyCallableType?>? {
    if (type is PyClassType && PyTypingTypeProvider.NEW_TYPE == type.classQName) {
      val newType = createNewType(call, context)
      if (newType != null) {
        val parameters = type.toClass().getParameters(context)
        return Ref.create(PyCallableTypeImpl(parameters, newType))
      }
    }
    return null
  }

  companion object {
    private fun createNewType(
      callExpression: PyCallExpression,
      context: TypeEvalContext,
    ): PyTypingNewTypeFactoryType? {
      if (context.maySwitchToAST(callExpression)) {
        val parent = callExpression.parent
        if (parent is PyAssignmentStatement) {
          val leftHandSideExpression = parent.leftHandSideExpression
          if (leftHandSideExpression is PyTargetExpression) {
            val stub = create(callExpression)
            if (stub != null) {
              val type = getClassType(stub, context, callExpression)
              if (type != null) {
                val newType = PyTypingNewType(type, stub.name, leftHandSideExpression)
                return PyTypingNewTypeFactoryType(newType)
              }
            }
          }
        }
      }
      return null
    }

    fun getNewTypeForResolvedElement(element: PsiElement, context: TypeEvalContext): PyTypingNewType? {
      if (element is PyTargetExpression) {
        return StubAwareComputation.on(element)
          .withCustomStub { it.getCustomStub(PyTypingNewTypeStub::class.java) }
          .overStub { getNewTypeFromStub(element, it, context) }
          .withStubBuilder { create(it) }
          .compute(context)
      }
      return null
    }

    private fun getNewTypeFromStub(
      target: PyTargetExpression,
      stub: PyTypingNewTypeStub?,
      context: TypeEvalContext,
    ): PyTypingNewType? {
      if (stub == null) return null
      val type = getClassType(stub, context, target)
      return if (type != null) PyTypingNewType(type, stub.name, target) else null
    }

    private fun getClassType(
      stub: PyTypingNewTypeStub,
      context: TypeEvalContext,
      anchor: PsiElement,
    ): PyClassType? {
      val type = Ref.deref(getStringBasedType(stub.classType, anchor, context))
      return if (type is PyClassType) type.toClass() else null
    }
  }
}
