// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.application
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@State(name = "MarkdownApplicationSettings",
       category = SettingsCategory.CODE,
       storages = [(Storage("markdown.xml"))])
class MarkdownApplicationSettings: SimplePersistentStateComponent<MarkdownApplicationSettings.State>(State()) {
  class State: BaseState() {
    var enableLivePreview: Boolean by property(true)
  }

  var enableLivePreview: Boolean
    get() = state.enableLivePreview
    set(value) { state.enableLivePreview = value }

  fun update(block: (MarkdownApplicationSettings) -> Unit) {
    block(this)
    application.messageBus.syncPublisher(ChangeListener.TOPIC).settingsChanged(this)
  }

  override fun noStateLoaded() {
    loadState(State())
  }

  @ApiStatus.OverrideOnly
  fun interface ChangeListener {
    fun settingsChanged(settings: MarkdownApplicationSettings)

    companion object {
      @JvmField
      @Topic.AppLevel
      val TOPIC: Topic<ChangeListener> = Topic("MarkdownApplicationSettingsChanged", ChangeListener::class.java)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): MarkdownApplicationSettings = service()

    suspend fun getInstanceAsync(): MarkdownApplicationSettings = serviceAsync()
  }
}
