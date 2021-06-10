// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.utils

object GoogleDocsImportUtils {
  private const val docsUrlPrefix = "https://docs.google.com/document/d/"
  private const val docsUrlSuffix = "/edit"

  private val docsRegEx: Regex get() = "$docsUrlPrefix(\\S*)$docsUrlSuffix".toRegex()

  fun isLinkToDocumentCorrect(link: String): Boolean = docsRegEx.containsMatchIn(link)

  fun extractDocsId(link: String) = link.removePrefix(docsUrlPrefix).removeSuffix(docsUrlSuffix)
}
