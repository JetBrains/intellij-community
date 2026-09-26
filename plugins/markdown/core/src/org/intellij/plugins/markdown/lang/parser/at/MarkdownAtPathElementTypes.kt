// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.at

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType

object MarkdownAtPathElementTypes {
  @JvmField
  val PATH_TOKEN: IElementType = MarkdownElementType("AT_PATH_TOKEN")

  @JvmField
  val PATH: IElementType = MarkdownElementType("AT_PATH")
}
