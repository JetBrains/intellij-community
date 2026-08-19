package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.getStringBasedType
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyTypingNewTypeStubImpl
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub
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
    val newType = Helper.getNewTypeForResolvedElement(referenceTarget, context)
    return if (newType != null) Ref.create(PyTypingNewTypeFactoryType(newType)) else null
  }

  object Helper {
    fun getNewTypeForResolvedElement(element: PsiElement, context: TypeEvalContext): PyTypingNewType? {
      if (element is PyTargetExpression) {
        return StubAwareComputation.on(element)
          .withCustomStub { it.getCustomStub(PyTypingNewTypeStub::class.java) }
          .overStub { getNewTypeFromStub(element, it, context) }
          .withStubBuilder { PyTypingNewTypeStubImpl.create(it) }
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
      val type = Ref.deref(getStringBasedType(stub.classType, target, context))
      return if (type is PyClassType) PyTypingNewType(type.toClass(), stub.name, target) else null
    }
  }
}
