// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.stubs

import com.intellij.psi.impl.source.xml.stub.XmlTagStubImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IElementType

open class HtmlStubBasedTagStubSerializer(elementType: IElementType) : XmlStubBasedStubSerializer<XmlTagStubImpl>(elementType) {
  override fun serialize(stub: XmlTagStubImpl, dataStream: StubOutputStream) {
    stub.serialize(dataStream)
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): XmlTagStubImpl =
    XmlTagStubImpl(parentStub, dataStream, elementType)
}