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

  /**
   * The file extensions of the files that the bundles of this provider support, without the leading dot.
   * The extensions are case-insensitive.
   *
   * A provider that returns a non-empty set is lazy: the TextMate plugin registers the extensions at startup
   * and reads the bundles on the first request of a file with such an extension.
   * A provider that returns an empty set has its bundles read at startup.
   */
  fun getExtensions(): Set<String> = emptySet()

  companion object {
    val EP_NAME: ExtensionPointName<TextMateBundleProvider> = ExtensionPointName("com.intellij.textmate.bundleProvider")
  }
}
