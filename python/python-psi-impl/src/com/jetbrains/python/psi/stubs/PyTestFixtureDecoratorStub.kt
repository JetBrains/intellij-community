package com.jetbrains.python.psi.stubs

import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStub

interface PyTestFixtureDecoratorStub : PyCustomDecoratorStub {
  val name: String
}
