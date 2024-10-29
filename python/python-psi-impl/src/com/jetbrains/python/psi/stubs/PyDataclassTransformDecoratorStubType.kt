package com.jetbrains.python.psi.stubs

import com.intellij.psi.stubs.StubInputStream
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStubType
import java.io.IOException

class PyDataclassTransformDecoratorStubType : PyCustomDecoratorStubType<PyDataclassTransformDecoratorStub> {
  override fun createStub(decorator: PyDecorator): PyDataclassTransformDecoratorStub? = PyDataclassTransformDecoratorStub.create(decorator)

  @Throws(IOException::class)
  override fun deserializeStub(stream: StubInputStream): PyDataclassTransformDecoratorStub = PyDataclassTransformDecoratorStub.deserialize(stream)
}
