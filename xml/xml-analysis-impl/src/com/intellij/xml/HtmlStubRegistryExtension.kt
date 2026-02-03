// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml

import com.intellij.psi.stubs.DefaultFileStubSerializer
import com.intellij.psi.stubs.StubRegistry
import com.intellij.psi.stubs.StubRegistryExtension
import com.intellij.psi.xml.XmlElementType

class HtmlStubRegistryExtension : StubRegistryExtension {
  override fun register(registry: StubRegistry) {
    registry.registerStubSerializer(XmlElementType.HTML_FILE, DefaultFileStubSerializer())
  }
}