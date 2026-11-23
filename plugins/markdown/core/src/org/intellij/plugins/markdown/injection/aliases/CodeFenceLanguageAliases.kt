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
    Entry("ASM", "asm", setOf("armasm", "assembly", "nasm", "gas", "x86asm")),
    Entry("go", "go", setOf("golang")),
    Entry("HCL", "hcl", setOf("hcl")),
    Entry("ApacheConfig", "apacheconf", setOf("aconf", "apache", "apacheconfig")),
    Entry("Batch", "batch", setOf("bat", "batchfile")),
    Entry("CoffeeScript", "coffeescript", setOf("coffee", "coffee-script")),
    Entry("JavaScript", "javascript", setOf("js", "node")),
    Entry("Markdown", "markdown", setOf("md")),
    Entry("PowerShell", "powershell", setOf("posh", "pwsh")),
    Entry("Python", "python", setOf("jupyter", "python2", "python3", "py")),
    Entry("R", "r", setOf("rlang", "rscript")),
    Entry("RegExp", "regexp", setOf("regex")),
    Entry("Ruby", "ruby", setOf("ruby", "rb")),
    Entry("Yaml", "yaml", setOf("yml")),
    Entry("Kotlin", "kotlin", setOf("kt", "kts", "gradle kotlin dsl")),
    Entry("HCL-Terraform", "terraform", setOf("hcl-terraform", "tf")),
    Entry("C#", "csharp", setOf("cs", "c#")),
    Entry("C++", "c++", setOf("cpp")),
    Entry("F#", "fsharp", setOf("fs", "f#")),
    Entry("Shell Script", "shell", setOf("shell script", "bash", "zsh", "sh", "console", "shell-script", "shellsession")),
    Entry("Groovy", "groovy", setOf("gradle")),
    Entry("Properties", "properties", setOf("java properties")),
    Entry("JSON", "json", setOf("geojson", "jsonld")),
    Entry("JSON5", "json5", setOf("jsonc")),
    Entry("JSON Lines", "json lines", setOf("jsonl")),
    Entry("Rust", "rust", setOf("rs")),
    Entry("Starlark", "starlark", setOf("bazel", "bzl")),
    Entry("protobuf", "protobuf", setOf("proto")),
    Entry("TEXT", "text", setOf("plain text")),
    Entry("Dockerfile", "dockerfile", setOf("containerfile")),
    Entry("Makefile", "makefile", setOf("make")),
    Entry("Ini", "ini", setOf("dosini")),
    Entry("ERB", "erb", setOf("html+erb")),
    Entry("Handlebars", "handlebars", setOf("hbs")),
    Entry("VTL", "vtl", setOf("velocity")),
    Entry("Jade", "jade", setOf("pug")),
    Entry("SvelteHTML", "sveltehtml", setOf("svelte")),
    Entry("ReST", "rest", setOf("restructuredtext", "rst")),
    Entry("WebAssembly", "webassembly", setOf("wasm")),
    Entry("VB", "vb", setOf("visual basic", "vbnet", "vb.net"))
  )

  fun findRegisteredEntry(value: String): String? {
    val lower = value.lowercase()
    val entry = aliases.singleOrNull { lower == it.main || lower in it.aliases }
    return entry?.id
  }

  /**
   * Get recommended alias for [id]
   * @return recommended alias if any or just [id]
   */
  fun findMainAlias(id: String): String {
    return findMainAliasIfRegistered(id) ?: StringUtil.toLowerCase(id)
  }

  fun findMainAliasIfRegistered(id: String): String? {
    return aliases.singleOrNull { id == it.id }?.main
  }
}
