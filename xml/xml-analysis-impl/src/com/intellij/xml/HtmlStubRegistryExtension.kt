// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.stubs.StubRegistry
import com.intellij.psi.stubs.StubRegistryExtension
import com.intellij.psi.stubs.StubSerializer
import com.intellij.psi.stubs.StubSerializerId
import com.intellij.psi.xml.XmlElementType

class HtmlStubRegistryExtension : StubRegistryExtension {
  override fun register(registry: StubRegistry) {
    registry.registerStubSerializer(XmlElementType.HTML_FILE, HtmlFileStubSerializer())
  }

  private class HtmlFileStubSerializer : StubSerializer<PsiFileStub<*>> {
    override fun getExternalId(): String {
      return StubSerializerId.DEFAULT_EXTERNAL_ID
    }

    override fun serialize(stub: PsiFileStub<*>, dataStream: StubOutputStream) {
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): PsiFileStub<*> {
      return PsiFileStubImpl(null)
    }

    override fun indexStub(stub: PsiFileStub<*>, sink: IndexSink) {
    }
  }
}