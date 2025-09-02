// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.stubs

import com.intellij.psi.impl.source.xml.stub.XmlAttributeStubImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IElementType

open class XmlStubBasedAttributeStubSerializer(elementType: IElementType) : XmlStubBasedStubSerializer<XmlAttributeStubImpl>(elementType) {
  override fun serialize(stub: XmlAttributeStubImpl, dataStream: StubOutputStream) {
    stub.serialize(dataStream)
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): XmlAttributeStubImpl =
    XmlAttributeStubImpl(parentStub, dataStream, elementType)
}