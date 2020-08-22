// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection.alias

import com.intellij.openapi.util.text.StringUtil

/**
 * Service to work with Markdown code fence's info-string.
 *
 * [LanguageInfoString] is able to find possible IntelliJ Language ID
 * for info string (including resolution of standard aliases) and
 * (backwards) suggest correct info string for IntelliJ Language ID
 */
internal object LanguageInfoString {
  private data class InfoStringData(val id: String, val main: String, val aliases: Set<String>)

  private val aliases by lazy {
    val lines = LanguageInfoString::class.java.getResourceAsStream("aliases.dat").use {
      it.reader().readLines().filterNot { line -> line.startsWith("#") }
    }.filter { it.isNotBlank() }

    val result = HashSet<InfoStringData>()

    for (line in lines) {
      val (id, alias, silent) = line.split("|").map { it.trim() }
      result.add(InfoStringData(id, alias, silent.split(",").map { it.trim() }.toSet()))
    }

    result
  }

  /**
   * Get possible IntelliJ Language ID for [alias].
   *
   * @return possible Language ID if any or [alias]
   */
  fun findId(alias: String): String {
    val lower = StringUtil.toLowerCase(alias)
    val id = aliases.singleOrNull { lower == it.main || lower in it.aliases }?.id
    return id ?: alias
  }

  /**
   * Get recommended alias for [id]
   * @return recommended alias if any or just [id]
   */
  fun findMainAlias(id: String): String {
    val alias = aliases.singleOrNull { id == it.id }?.main
    return alias ?: StringUtil.toLowerCase(id)
  }
}