package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.getStringBasedType
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyFunction
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
  override fun getCallType(
    function: PyFunction,
    callSite: PyCallSiteExpression,
    context: TypeEvalContext,
  ): Ref<PyType?>? {
    if (callSite is PyCallExpression &&
        PyTypingTypeProvider.NEW_TYPE == function.qualifiedName
    ) {
      val newType = createNewType(callSite, context)
      if (newType != null) {
        return Ref.create<PyType?>(newType)
      }
    }
    return null
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType?>? {
    val newType: PyTypingNewType? = getNewTypeForResolvedElement(referenceTarget, context)
    if (newType != null) {
      return Ref.create<PyType?>(PyTypingNewTypeFactoryType(newType))
    }
    return null
  }

  override fun prepareCalleeTypeForCall(
    type: PyType?,
    call: PyCallExpression,
    context: TypeEvalContext,
  ): Ref<PyCallableType?>? {
    if (type is PyClassType && PyTypingTypeProvider.NEW_TYPE == type.classQName) {
      val newType: PyTypingNewTypeFactoryType? = createNewType(call, context)
      if (newType != null) {
        val parameters = type.toClass().getParameters(context)
        return Ref.create<PyCallableType?>(PyCallableTypeImpl(parameters, newType))
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
              val type: PyClassType? = getClassType(stub, context, callExpression)
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
          .withCustomStub { stub ->
            stub.getCustomStub(PyTypingNewTypeStub::class.java)
          }
          .overStub { customStub ->
            getNewTypeFromStub(
              element,
              customStub,
              context
            )
          }
          .withStubBuilder { expression -> create(expression) }
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
      val type: PyClassType? = getClassType(stub, context, target)
      return if (type != null) PyTypingNewType(type, stub.name, target) else null
    }

    private fun getClassType(
      stub: PyTypingNewTypeStub,
      context: TypeEvalContext,
      anchor: PsiElement,
    ): PyClassType? {
      val type = Ref.deref<PyType?>(getStringBasedType(stub.classType, anchor, context))
      if (type is PyClassType) {
        return type.toClass()
      }
      return null
    }
  }
}
