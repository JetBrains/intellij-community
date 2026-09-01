package com.intellij.markdown.figmaAdvertiser

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.MarkdownFigmaAdvertiserBundle"

/**
 * The advertisement's own bundle, separate from `MarkdownBundle`.
 *
 * The Markdown plugin's own strings and this advertisement's are edited by different people: the
 * offer text is Figma Connect's marketing copy, and a bundle of its own keeps a wording change out
 * of the file every Markdown string lives in.
 */
@ApiStatus.Internal
object MarkdownFigmaAdvertiserBundle {
  private val bundle = DynamicBundle(this::class.java, BUNDLE)

  @Nls
  fun message(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg params: Any,
  ): String = bundle.getMessage(key, *params)
}
