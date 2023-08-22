package com.jetbrains.python

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.stubs.StubIndexKey
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.stubs.PyDecoratorStub
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
interface PyCustomDecoratorIndexer {
  fun getKey(): StubIndexKey<String, PyDecorator>
  fun getKeyForStub(decorator: PyDecoratorStub): String?
  companion object {
    @JvmField val EP_NAME = ExtensionPointName.create<PyCustomDecoratorIndexer>("Pythonid.decoratorIndexer")
  }
}

