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

  val isAdvertiserEnabled: Boolean
    get() = Registry.`is`(KEY_ADVERTISER_ENABLED, ENABLED_BY_DEFAULT)
}
