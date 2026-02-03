package org.intellij.plugins.markdown.injection.aliases

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AdditionalFenceLanguageSuggester {
  fun suggestLanguage(name: String): Language?
}
