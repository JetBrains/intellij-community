// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.html.dtd

interface HtmlSymbolDeclaration {

  val kind: Kind
  val name: String?

  enum class Kind {
    ELEMENT,
    ATTRIBUTE,
    ATTRIBUTE_VALUE
  }

}