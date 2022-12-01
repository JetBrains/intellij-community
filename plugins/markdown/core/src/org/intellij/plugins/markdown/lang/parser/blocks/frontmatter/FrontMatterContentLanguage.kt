package org.intellij.plugins.markdown.lang.parser.blocks.frontmatter

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class FrontMatterContentLanguage(val id: String)

@ApiStatus.Experimental
object FrontMatterLanguages {
  val YAML = FrontMatterContentLanguage("yaml")
  val TOML = FrontMatterContentLanguage("TOML")
}
