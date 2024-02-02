package org.jetbrains.plugins.textmate

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.textmate.configuration.TextMatePersistentBundle

class TextMateServiceImplExtensions {
  companion object{
    fun getPluginBundles(): MutableMap<String, TextMatePersistentBundle> {
      val bundleProviders = TextMateBundleProvider.EP_NAME.extensionList
      val bundles = mutableMapOf<String, TextMatePersistentBundle>()
      for (provider in bundleProviders) {
        try {
          provider.provideBundles().forEach {
            bundles[it.key] = TextMatePersistentBundle(it.value.name, true)
          }
        } catch (e: Exception) {
          thisLogger().error("${provider} failed", e)
        }
      }
      return bundles
    }
  }
}

data class PluginBundle(val name: String)

interface TextMateBundleProvider {
  /**
   * @return A mutable map of bundles, where key is a path to bundle folder on disk.
   */
  fun provideBundles(): MutableMap<String, PluginBundle>

  companion object{
    val EP_NAME : ExtensionPointName<TextMateBundleProvider> = ExtensionPointName("com.intellij.textmate.bundleProvider")
  }
}
