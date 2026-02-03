package org.intellij.plugins.markdown.fenceInjection

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FenceSurroundingsProvider {
  data class Surroundings(val prefix: String?, val suffix: String?)

  val language: Language
  fun getCodeFenceSurroundings(): Surroundings

  companion object {
    val EP_NAME: ExtensionPointName<FenceSurroundingsProvider> = ExtensionPointName<FenceSurroundingsProvider>(
      "org.intellij.plugins.markdown.fenceInjection.fenceSurroundingsProvider"
    )
  }
}
