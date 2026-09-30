// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.StateStorageChooserEx
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@Suppress("DEPRECATION")
@Deprecated("Use MarkdownSettings.getInstance()", ReplaceWith("MarkdownSettings.getInstance()"))
@ApiStatus.Experimental
@Service(Service.Level.APP)
@com.intellij.openapi.components.State(name = "MarkdownPreviewSettings", storages = [])
class MarkdownPreviewSettings: SimplePersistentStateComponent<MarkdownPreviewSettings.State>(State(delegateToApplication = true)),
                              StateStorageChooserEx {
  class State internal constructor(private val delegateToApplication: Boolean): BaseState() {
    constructor(): this(false)

    private var storedFontSize = MarkdownSettings.defaultFontSize

    var fontSize: Int
      get() = if (delegateToApplication) MarkdownSettings.getInstance().fontSize else storedFontSize
      set(value) {
        if (delegateToApplication) MarkdownSettings.getInstance().fontSize = value
        else storedFontSize = value
      }
  }

  fun update(block: (MarkdownPreviewSettings) -> Unit) {
    MarkdownSettings.getInstance().update { block(this) }
  }

  override fun getResolution(storage: Storage, operation: StateStorageOperation): StateStorageChooserEx.Resolution =
    StateStorageChooserEx.Resolution.SKIP

  fun interface ChangeListener {
    fun settingsChanged(settings: MarkdownPreviewSettings)

    companion object {
      @JvmField
      @Topic.AppLevel
      val TOPIC = Topic("MarkdownPreviewSettingsChanged", ChangeListener::class.java)
    }
  }
}
