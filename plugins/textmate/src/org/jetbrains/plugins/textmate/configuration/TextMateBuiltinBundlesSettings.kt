package org.jetbrains.plugins.textmate.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.plugins.textmate.TextMateBundleToLoad

@State(name = "TextMateBuiltinBundlesSettings",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "textmateBuiltinBundles.xml", roamingType = RoamingType.DISABLED)])
class TextMateBuiltinBundlesSettings : SerializablePersistentStateComponent<TextMateBuiltinBundlesSettings.State>(State()) {
  companion object {
    @JvmStatic
    val instance: TextMateBuiltinBundlesSettings?
      get() = ApplicationManager.getApplication().getService(TextMateBuiltinBundlesSettings::class.java)
  }

  var builtinBundles: List<TextMateBundleToLoad> = emptyList()

  fun setTurnedOffBundleNames(names: Collection<String>) {
    updateState { State(names.sorted()) }
  }

  override fun noStateLoaded() {
    loadState(State())
  }

  fun getTurnedOffBundleNames(): Set<String> = state.turnedOffBundleNames.toSet()

  data class State(
    @JvmField
    val turnedOffBundleNames: List<String> = emptyList()
  )
}