package com.jetbrains.python

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class PythonDocumentationHighlightingService {
  companion object{
    @JvmStatic
    fun getInstance(): PythonDocumentationHighlightingService {
      return service()
    }
  }

  open fun highlightedCodeSnippet(project: Project, codeSnippet: String): String = codeSnippet
  open fun styledSpan(textAttributeKey: TextAttributesKey, text: String): String = text
}
