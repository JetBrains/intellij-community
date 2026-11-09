// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang

import com.intellij.openapi.util.registry.RegistryManager

internal object MarkdownCommentSyntax {
  const val LINK_DEFINITION_SYNTAX_REGISTRY_KEY = "markdown.line.comment.link.definition.syntax.enabled"

  fun shouldWriteLinkDefinitionSyntax(): Boolean {
    return RegistryManager.getInstance().`is`(LINK_DEFINITION_SYNTAX_REGISTRY_KEY)
  }
}
