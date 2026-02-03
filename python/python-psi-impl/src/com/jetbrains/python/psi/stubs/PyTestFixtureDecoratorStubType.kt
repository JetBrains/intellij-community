package com.jetbrains.python.psi.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStubType
import com.jetbrains.python.testing.pyTestFixtures.TEST_FIXTURE_DECORATOR_NAMES
import com.jetbrains.python.testing.pyTestFixtures.getTestFixtureName

class PyTestFixtureDecoratorStubType : PyCustomDecoratorStubType<PyTestFixtureDecoratorStub> {
  override fun createStub(psi: PyDecorator): PyTestFixtureDecoratorStub? {
    val qName = psi.getQualifiedName()
    if (qName == null || qName.toString() !in TEST_FIXTURE_DECORATOR_NAMES) {
      return null
    }
    val testFixtureName = getTestFixtureName(psi) ?: return null
    return PyTestFixtureDecoratorStubImpl(testFixtureName)
  }

  override fun deserializeStub(stream: StubInputStream): PyTestFixtureDecoratorStub? {
    val name = stream.readNameString() ?: return null
    return PyTestFixtureDecoratorStubImpl(name)
  }
}

private class PyTestFixtureDecoratorStubImpl(override val name: String) : PyTestFixtureDecoratorStub {
  override fun getTypeClass(): Class<PyTestFixtureDecoratorStubType> = PyTestFixtureDecoratorStubType::class.java

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(name)
  }

  override fun toString(): String {
    return "PyTestFixtureDecoratorStub(name='$name')"
  }
}