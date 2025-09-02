// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.stubs

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubSerializer
import com.intellij.psi.tree.IElementType

abstract class XmlStubBasedStubSerializer<T : StubElement<*>>(val elementType: IElementType) : StubSerializer<T> {
  override fun getExternalId(): String =
    elementType.toString()

  override fun indexStub(stub: T, sink: IndexSink) {
  }
}