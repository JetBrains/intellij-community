package org.jetbrains.plugins.textmate.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.plugins.textmate.TextMateBundleToLoad
import java.nio.file.Path

@State(name = "TextMateBuiltinBundlesSettings", storages = [Storage(value = "textmateBuiltinBundles.xml")])
@Service(Service.Level.APP)
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

  fun getTurnedOffBundleNames(): Set<String> = state.turnedOffBundleNames.toSet()

  data class State(
    @JvmField
    val turnedOffBundleNames: List<String> = emptyList()
  )
}