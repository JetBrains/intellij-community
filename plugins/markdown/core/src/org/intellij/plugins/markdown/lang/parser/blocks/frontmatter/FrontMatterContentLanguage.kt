package org.intellij.plugins.markdown.lang.parser.blocks.frontmatter

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class FrontMatterContentLanguage(private val name: String)

@ApiStatus.Experimental
object FrontMatterLanguages {
  val YAML = FrontMatterContentLanguage("YAML")
  val TOML = FrontMatterContentLanguage("TOML")
}
