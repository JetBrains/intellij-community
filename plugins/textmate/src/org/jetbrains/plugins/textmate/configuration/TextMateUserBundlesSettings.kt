package org.jetbrains.plugins.textmate.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.FileUtil
import kotlinx.serialization.Serializable

@Serializable
data class TextMatePersistentBundle(val name: String, val enabled: Boolean)

@State(name = "TextMateUserBundlesSettings",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "textmate.xml", roamingType = RoamingType.DISABLED)])
class TextMateUserBundlesSettings : SerializablePersistentStateComponent<TextMateUserBundleServiceState>(TextMateUserBundleServiceState()) {
  val bundles: Map<String, TextMatePersistentBundle>
    get() = state.bundles

  fun setBundlesConfig(bundles: Map<String, TextMatePersistentBundle>) {
    updateState {
      TextMateUserBundleServiceState(bundles.mapKeys { (path, _) -> FileUtil.toSystemIndependentName(path) })
    }
  }

  override fun noStateLoaded() {
    loadState(TextMateUserBundleServiceState())
  }

  fun addBundle(path: String, name: String) {
    val normalizedPath = FileUtil.toSystemIndependentName(path)
    updateState { state ->
      TextMateUserBundleServiceState(state.bundles + (normalizedPath to TextMatePersistentBundle(name, enabled = true)))
    }
  }

  fun removeBundle(path: String) {
    val normalizedPath = FileUtil.toSystemIndependentName(path)
    updateState { state ->
      TextMateUserBundleServiceState(state.bundles.filter { it.key != normalizedPath })
    }
  }

  fun disableBundle(path: String) {
    val normalizedPath = FileUtil.toSystemIndependentName(path)
    updateState { state ->
      state.bundles[normalizedPath]?.let { bundle ->
        TextMateUserBundleServiceState(state.bundles + (normalizedPath to bundle.copy(enabled = false)))
      } ?: state
    }
  }

  fun hasEnabledBundle(path: String): Boolean {
    val normalizedPath = FileUtil.toSystemIndependentName(path)
    return bundles[normalizedPath]?.enabled == true
  }

  companion object {
    @JvmStatic
    fun getInstance(): TextMateUserBundlesSettings? {
      return ApplicationManager.getApplication().getService(TextMateUserBundlesSettings::class.java)
    }
  }
}

@Serializable
data class TextMateUserBundleServiceState(
  @JvmField
  val bundles: Map<String, TextMatePersistentBundle> = emptyMap()
)
