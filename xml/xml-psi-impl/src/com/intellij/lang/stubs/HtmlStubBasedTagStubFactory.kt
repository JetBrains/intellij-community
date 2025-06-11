// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.HtmlStubBasedTagImpl
import com.intellij.psi.impl.source.xml.stub.XmlTagStubImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubElementFactory
import com.intellij.psi.tree.IElementType

open class HtmlStubBasedTagStubFactory(val elementType: IElementType) : StubElementFactory<XmlTagStubImpl, HtmlStubBasedTagImpl> {
  override fun createStub(psi: HtmlStubBasedTagImpl, parentStub: StubElement<out PsiElement>?): XmlTagStubImpl =
    XmlTagStubImpl(psi, parentStub, elementType)

  override fun createPsi(stub: XmlTagStubImpl): HtmlStubBasedTagImpl =
    HtmlStubBasedTagImpl(stub, elementType)
}