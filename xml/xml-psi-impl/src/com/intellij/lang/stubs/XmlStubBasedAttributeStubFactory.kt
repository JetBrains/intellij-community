// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.XmlStubBasedAttribute
import com.intellij.psi.impl.source.xml.stub.XmlAttributeStubImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubElementFactory
import com.intellij.psi.tree.IElementType

open class XmlStubBasedAttributeStubFactory(val elementType: IElementType) : StubElementFactory<XmlAttributeStubImpl, XmlStubBasedAttribute> {
  override fun createStub(psi: XmlStubBasedAttribute, parentStub: StubElement<out PsiElement>?): XmlAttributeStubImpl =
    XmlAttributeStubImpl(psi, parentStub, elementType)

  override fun createPsi(stub: XmlAttributeStubImpl): XmlStubBasedAttribute =
    XmlStubBasedAttribute(stub, elementType)
}