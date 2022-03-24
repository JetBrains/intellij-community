// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.injection.aliases

import com.intellij.openapi.util.text.StringUtil

/**
 * Service to work with Markdown code fence's info-string.
 *
 * [CodeFenceLanguageAliases] is able to find possible IntelliJ Language ID
 * for info string (including resolution of standard aliases) and
 * (backwards) suggest correct info string for IntelliJ Language ID
 */
internal object CodeFenceLanguageAliases {
  private data class Entry(
    val id: String,
    val main: String,
    val aliases: Set<String>
  )

  private val aliases = setOf(
    Entry("go", "go", setOf("golang")),
    Entry("HCL", "hcl", setOf("hcl")),
    Entry("ApacheConfig", "apacheconf", setOf("aconf", "apache", "apacheconfig")),
    Entry("Batch", "batch", setOf("bat", "batchfile")),
    Entry("CoffeeScript", "coffeescript", setOf("coffee", "coffee-script")),
    Entry("JavaScript", "javascript", setOf("js", "node")),
    Entry("Markdown", "markdown", setOf("md")),
    Entry("PowerShell", "powershell", setOf("posh", "pwsh")),
    Entry("Python", "python", setOf("python2", "python3", "py")),
    Entry("R", "r", setOf("rlang", "rscript")),
    Entry("RegExp", "regexp", setOf("regex")),
    Entry("Ruby", "ruby", setOf("ruby", "rb")),
    Entry("Yaml", "yaml", setOf("yml")),
    Entry("Kotlin", "kotlin", setOf("kt", "kts")),
    Entry("HCL-Terraform", "terraform", setOf("hcl-terraform", "tf")),
    Entry("C#", "csharp", setOf("cs", "c#")),
    Entry("Shell Script", "shell", setOf("shell script", "bash", "zsh", "sh"))
  )

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
