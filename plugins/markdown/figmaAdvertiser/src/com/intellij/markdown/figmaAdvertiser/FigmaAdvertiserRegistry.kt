package com.intellij.markdown.figmaAdvertiser

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * The advertisement's own switch, independent of Figma Connect's own keys.
 *
 * The plugin declares `figma.*` keys of its own, and none of them can serve here: they ship inside
 * the plugin, and this advertisement runs exactly where that plugin is absent. Someone who switches
 * the integration off and leaves this on still gets the offer.
 *
 * [ENABLED_BY_DEFAULT] is written twice, here and as `defaultValue` on the `<registryKey>`
 * declaration in `intellij.markdown.figmaAdvertiser.xml`. The declaration is what a running IDE
 * reads; this constant answers before the declaration is loaded. The two must agree.
 */
@ApiStatus.Internal
object FigmaAdvertiserRegistry {
  const val KEY_ADVERTISER_ENABLED: String = "markdown.figma.advertiser.enabled"

  const val ENABLED_BY_DEFAULT: Boolean = true

  /**
   * The platform's own "stop offering me plugins" switch, read at
   * `PluginsAdvertiserStartupActivity.kt:50`.
   */
  const val KEY_PLATFORM_PLUGIN_SUGGESTIONS: String = "ide.show.plugin.suggestions.on.open"

  val isAdvertiserEnabled: Boolean
    get() = Registry.`is`(KEY_ADVERTISER_ENABLED, ENABLED_BY_DEFAULT)

  /**
   * Whether the advertisement may offer anything, before any file's path or text is looked at.
   *
   * The platform reads [KEY_PLATFORM_PLUGIN_SUGGESTIONS] before the project-open balloon and not
   * before an editor banner, so the platform's own banners ignore it. This advertisement answers it,
   * because the two mistakes cost different amounts. Honouring the switch too widely costs a user one
   * suggestion they might have wanted. Ignoring it shows a plugin offer to a user who asked to be
   * shown none, which is the worse of the two, so the doubt is spent on the side of their answer.
   *
   * Both questions are registry reads, which is why every caller asks this one first.
   */
  val isSuggestionAllowed: Boolean
    get() = isAdvertiserEnabled && Registry.`is`(KEY_PLATFORM_PLUGIN_SUGGESTIONS)
}
