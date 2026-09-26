// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.testlink

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType

object TestLinkElementTypes {
  const val LABEL_NAME: String = "@test"

  @JvmField
  val LINK: IElementType = MarkdownElementType("MARKDOWN_TEST_LINK")
}
