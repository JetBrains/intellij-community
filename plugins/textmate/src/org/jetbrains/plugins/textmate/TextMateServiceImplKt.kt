package org.jetbrains.plugins.textmate

import com.intellij.openapi.diagnostic.getOrHandleException
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import kotlin.io.path.absolutePathString

/**
 * @param extensions the lower-case extensions that the provider declares, empty for a provider whose bundle is read at startup
 */
internal class PluginBundleToLoad(val bundle: TextMateBundleToLoad, val extensions: Set<String>)

internal fun getPluginBundles(): List<PluginBundleToLoad> {
  val pluginBundles = ArrayList<PluginBundleToLoad>()
  for (provider in TextMateBundleProvider.EP_NAME.extensionList) {
    runCatching {
      val extensions = provider.getExtensions().map { it.lowercase() }.toSet()
      provider.getBundles().mapTo(pluginBundles) { PluginBundleToLoad(TextMateBundleToLoad(it.name, it.path.absolutePathString()), extensions) }
    }.getOrHandleException {
      TextMateService.LOG.error("$provider failed", it)
    }
  }
  return pluginBundles.distinctBy { it.bundle.path }
}
