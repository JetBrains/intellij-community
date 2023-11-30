package com.jetbrains.python.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.impl.stubs.PyDecoratorCallElementType.evaluateArgumentValue

import com.jetbrains.python.psi.stubs.PyDecoratorStub

fun PyDecorator.getNamedArgument(name: String): String? {
  return when(val stub = (this as StubBasedPsiElementBase<PyDecoratorStub>).greenStub) {
    null -> getKeywordArgument(name)?.let(::evaluateArgumentValue)
    else -> stub.getNamedArgumentLiteralText(name)
  }
}

