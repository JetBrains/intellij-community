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
class TextMateUserBundlesSettings : SerializablePersistentStateComponent<TextMateUserBundlesSettings.State>(State()) {
  val bundles: Map<String, TextMatePersistentBundle>
    get() = state.bundles

  fun setBundlesConfig(bundles: Map<String, TextMatePersistentBundle>) {
    updateState {
      State(bundles.mapKeys { (path, _) -> FileUtil.toSystemIndependentName(path) })
    }
  }

  fun addBundle(path: String, name: String) {
    val normalizedPath = FileUtil.toSystemIndependentName(path)
    updateState { state ->
      State(state.bundles + (normalizedPath to TextMatePersistentBundle(name, enabled = true)))
    }
  }

  fun disableBundle(path: String) {
    val normalizedPath = FileUtil.toSystemIndependentName(path)
    updateState { state ->
      state.bundles[normalizedPath]?.let { bundle ->
        State(state.bundles + (normalizedPath to bundle.copy(enabled = false)))
      } ?: state
    }
  }

  data class State(
    @JvmField
    val bundles: Map<String, TextMatePersistentBundle> = emptyMap()
  )

  companion object {
    @JvmStatic
    val instance: TextMateUserBundlesSettings?
      get() = ApplicationManager.getApplication().getService(TextMateUserBundlesSettings::class.java)
  }
}
