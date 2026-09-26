package org.jetbrains.plugins.textmate.api

import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

/**
 * Plugins may register their implementations of the `TextMateBundleProvider` to supply TextMate plugin with additional list of bundles.
 * Lifetime of those bundles matches the lifetime of a plugin
 */
interface TextMateBundleProvider {
  /**
   * This data class represents a bundle with a name and a path.
   */
  data class PluginBundle(val name: String, val path: Path)

  /**
   * Returns a list of TextMate bundles provided by extension.
   */
  fun getBundles(): List<PluginBundle>

  companion object {
    val EP_NAME: ExtensionPointName<TextMateBundleProvider> = ExtensionPointName("com.intellij.textmate.bundleProvider")
  }
}