package org.intellij.plugins.markdown.lang.parser.blocks.frontmatter

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class FrontMatterContentLanguage(val id: String)

@ApiStatus.Experimental
object FrontMatterLanguages {
  val YAML = FrontMatterContentLanguage("yaml")
  val TOML = FrontMatterContentLanguage("TOML")
}

@ApiStatus.Experimental
fun FrontMatterContentLanguage.findLanguage(): Language? {
  return Language.findLanguageByID(id)
}
