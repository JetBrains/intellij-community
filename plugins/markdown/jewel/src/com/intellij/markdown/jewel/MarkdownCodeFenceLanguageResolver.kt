// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jewel

import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.jetbrains.jewel.bridge.code.highlighting.codefence.CodeFenceLanguage
import org.jetbrains.jewel.bridge.code.highlighting.codefence.CodeFenceLanguageResolver
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * Gives Jewel the code fence languages that the Markdown extension points provide.
 */
@OptIn(InternalJewelApi::class)
internal class MarkdownCodeFenceLanguageResolver : CodeFenceLanguageResolver {
  override fun resolve(infoString: String): CodeFenceLanguage? {
    val (language, extension) = CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection(infoString) ?: return null
    return CodeFenceLanguage(language, extension)
  }
}
