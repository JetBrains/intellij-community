// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PythonDocumentationHighlightingServiceImpl : PythonDocumentationHighlightingService() {
  override fun highlightedCodeSnippet(project: Project, codeSnippet: String): String {
    return QuickDocHighlightingHelper.getStyledCodeFragment(project, PythonLanguage.INSTANCE, codeSnippet)
  }

  override fun styledSpan(textAttributeKey: TextAttributesKey, text: String): String {
    return QuickDocHighlightingHelper.getStyledFragment(text, textAttributeKey)
  }
}
