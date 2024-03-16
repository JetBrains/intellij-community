package org.jetbrains.plugins.textmate

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import kotlin.io.path.absolutePathString

internal fun getPluginBundles(): MutableList<TextMateBundleToLoad> {
  val bundleProviders = TextMateBundleProvider.EP_NAME.extensionList
  val pluginBundles = mutableListOf<TextMateBundleProvider.PluginBundle>()
  for (provider in bundleProviders) {
    try {
      pluginBundles.addAll(provider.getBundles())
    }
    catch (e: Exception) {
      logger<TextMateServiceImpl>().error("${provider} failed", e)
    }
  }
  return pluginBundles.distinctBy { it.path }.mapTo(mutableListOf()) { TextMateBundleToLoad(it.name, it.path.absolutePathString()) }
}