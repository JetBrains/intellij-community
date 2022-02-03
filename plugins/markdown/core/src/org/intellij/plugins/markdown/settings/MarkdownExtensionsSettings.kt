// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service(Service.Level.APP)
@State(name = "MarkdownExtensionsSettings", storages = [Storage("markdown.xml")], category = SettingsCategory.TOOLS)
class MarkdownExtensionsSettings: SimplePersistentStateComponent<MarkdownExtensionsSettings.State>(State()) {
  class State: BaseState() {
    @get:XMap
    var enabledExtensions by map<String, Boolean>()
  }

  var extensionsEnabledState
    get() = state.enabledExtensions
    set(value) { state.enabledExtensions = value }

  fun isExtensionEnabled(extensionsId: String): Boolean {
    return state.enabledExtensions[extensionsId] == true
  }

  @ApiStatus.Experimental
  fun interface ChangeListener {
    /**
     * @param fromSettingsDialog true if extensions state was changed from IDE settings dialog.
     */
    fun extensionsSettingsChanged(fromSettingsDialog: Boolean)

    companion object {
      @Topic.AppLevel
      @JvmField
      val TOPIC = Topic.create("MarkdownExtensionsSettingsChanged", ChangeListener::class.java)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): MarkdownExtensionsSettings {
      return service()
    }
  }
}
