package org.jetbrains.plugins.textmate.configuration

class TextMateConfigurableData(private val builtinBundlesSettings: TextMateBuiltinBundlesSettings,
                               private val userBundlesSettings: TextMateUserBundlesSettings) {
  companion object {
    @JvmStatic
    fun getInstance(): TextMateConfigurableData? {
      return TextMateBuiltinBundlesSettings.instance?.let { builtinBundles ->
        TextMateUserBundlesSettings.instance?.let { userBundles ->
          TextMateConfigurableData(builtinBundles, userBundles)
        }
      }
    }
  }

  fun applySettings(bundles: Set<TextMateConfigurableBundle>) {
    val (builtinBundles, userBundles) = bundles.partition { bundle -> bundle.builtin }
    builtinBundlesSettings.setTurnedOffBundleNames(builtinBundles.filter { !it.enabled }.map { bundle -> bundle.name })
    userBundlesSettings.setBundlesConfig(userBundles.associate { bundle ->
      bundle.path to TextMatePersistentBundle(bundle.name, bundle.enabled)
    })
  }

  fun getConfigurableBundles(): Set<TextMateConfigurableBundle> {
    return buildSet {
      val turnedOffBundleNames = builtinBundlesSettings.getTurnedOffBundleNames()
      addAll(builtinBundlesSettings.builtinBundles.map { path ->
        TextMateConfigurableBundle(path.name, path.path, enabled = !turnedOffBundleNames.contains(path.name), builtin = true)
      })
      addAll(userBundlesSettings.bundles.map { (path, bundle) ->
        TextMateConfigurableBundle(bundle.name, path, enabled = bundle.enabled, builtin = false)
      })
    }
  }
}