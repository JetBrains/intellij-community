package com.jetbrains.python.psi.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStub
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStubType

class PyFunctoolsWrapsDecoratorStubType : PyCustomDecoratorStubType<PyFunctoolsWrapsDecoratorStub> {
  override fun createStub(psi: PyDecorator): PyFunctoolsWrapsDecoratorStub? {
    return PyFunctoolsWrapsDecoratorStub.create(psi)
  }

  override fun deserializeStub(stream: StubInputStream): PyFunctoolsWrapsDecoratorStub? {
    val name = stream.readNameString() ?: return null
    return PyFunctoolsWrapsDecoratorStub(name)
  }
}

class PyFunctoolsWrapsDecoratorStub(val wrapped: String) : PyCustomDecoratorStub {
  override fun getTypeClass(): Class<out PyCustomDecoratorStubType<*>> = PyFunctoolsWrapsDecoratorStubType::class.java

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(wrapped)
  }

  override fun toString(): String {
    return "PyFunctoolsWrapsDecoratorStub(wrapped='$wrapped')"
  }

  companion object {
    fun create(psi: PyDecorator): PyFunctoolsWrapsDecoratorStub? {
      val qName = psi.qualifiedName ?: return null
      if (!PyKnownDecoratorUtil.asKnownDecorators(qName).contains(PyKnownDecorator.FUNCTOOLS_WRAPS)) return null
      val wrappedExpr = psi.argumentList?.getValueExpressionForParam(PyKnownDecoratorUtil.FunctoolsWrapsParameters.WRAPPED) as? PyReferenceExpression
      val wrappedExprQName = wrappedExpr?.asQualifiedName() ?: return null
      return PyFunctoolsWrapsDecoratorStub(wrappedExprQName.toString())
    }
  }
}