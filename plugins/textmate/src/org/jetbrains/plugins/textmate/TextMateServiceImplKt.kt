package org.jetbrains.plugins.textmate

import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import kotlin.io.path.absolutePathString

fun TextMateServiceImpl.getPluginBundles(): MutableList<TextMateBundleToLoad> {
  val bundleProviders = TextMateBundleProvider.EP_NAME.extensionList
  val pluginBundles = mutableListOf<TextMateBundleProvider.PluginBundle>()
  for (provider in bundleProviders) {
    runCatching {
      pluginBundles.addAll(provider.getBundles())
    }.getOrHandleException {
      thisLogger().error("$$provider failed", it)
    }
  }
  return pluginBundles.distinctBy { it.path }.mapTo(mutableListOf()) { TextMateBundleToLoad(it.name, it.path.absolutePathString()) }
}