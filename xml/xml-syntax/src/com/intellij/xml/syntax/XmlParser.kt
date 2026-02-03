// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.syntax

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder

class XmlParser {
  fun parse(builder: SyntaxTreeBuilder) {
    val file = builder.mark()
    XmlParsing(builder).parseDocument()
    file.done(XmlSyntaxElementType.XML_FILE)
  }
}