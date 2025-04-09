/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package com.intellij.toml.frontend.split

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.jetbrains.rd.ide.model.HighlighterModel
import com.jetbrains.rdclient.daemon.FrontendHighlighterSuppressionHandler
import org.toml.lang.psi.TomlFileType

class TomlFrontendHighlightingSuppressor : FrontendHighlighterSuppressionHandler {
  override fun shouldSuppress(highlighterModel: HighlighterModel, document: Document): Boolean {
    return when {
      FileDocumentManager.getInstance().getFile(document)?.fileType != TomlFileType -> false
      isTomlSyntaxHighlightingFromBackend(highlighterModel.properties.attributeId) -> true
      else -> false
    }
  }

  private fun isTomlSyntaxHighlightingFromBackend(highlighterId: String): Boolean {
    return highlighterId.startsWith(TOML_BACKEND_HIGHLIGHTER_ID)
  }
}

private const val TOML_BACKEND_HIGHLIGHTER_ID = "IJ.org.toml"