// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang

import com.intellij.codeInsight.template.FileTypeBasedContextType
import org.intellij.plugins.markdown.MarkdownBundle

class MarkdownContextType : FileTypeBasedContextType(
  MarkdownBundle.message("markdown.live.template.context.name"),
  MarkdownFileType.INSTANCE
)
