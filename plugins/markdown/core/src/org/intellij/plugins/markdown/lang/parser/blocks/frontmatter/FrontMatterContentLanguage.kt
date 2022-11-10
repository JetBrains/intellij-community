package org.intellij.plugins.markdown.lang.parser.blocks.frontmatter

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class FrontMatterContentLanguage(val name: String)

@ApiStatus.Experimental
object FrontMatterLanguages {
  val YAML = FrontMatterContentLanguage("YAML")
  val TOML = FrontMatterContentLanguage("TOML")
}

@ApiStatus.Experimental
fun FrontMatterContentLanguage.findLanguage(): Language? {
  return Language.findLanguageByID(name.lowercase())
}
