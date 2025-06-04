// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.terminal.TerminalFontSizeProvider
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.APP)
@ApiStatus.Internal
class TerminalFontSizeProviderImpl : TerminalFontSizeProvider, Disposable {
  private val listeners = CopyOnWriteArrayList<TerminalFontSizeProvider.Listener>()

  private var fontSize: TerminalFontSize? = null

  init {
    val connection = ApplicationManager.getApplication().getMessageBus().connect(this)
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      resetFontSize() // presentation mode, Zoom IDE...
    })

    TerminalFontSettingsService.getInstance().addListener(object : TerminalFontSettingsListener {
      override fun fontSettingsChanged() {
        resetFontSize()
      }
    }, disposable = this)
  }

  override fun getFontSize(): Float {
    var size = fontSize
    if (size == null) {
      size = getDefaultScaledFontSize()
      fontSize = size
    }
    return size.floatValue
  }

  override fun setFontSize(newSize: Float) {
    setFontSizeImpl(newSize, showZoomIndicator = true)
  }

  override fun resetFontSize() {
    setFontSizeImpl(getDefaultScaledFontSize().floatValue, showZoomIndicator = false)
  }

  private fun setFontSizeImpl(newSize: Float, showZoomIndicator: Boolean) {
    val oldSize = fontSize
    if (oldSize != TerminalFontSize.ofFloat(newSize)) {
      fontSize = TerminalFontSize.ofFloat(newSize)
      for (listener in listeners) {
        listener.fontChanged(showZoomIndicator)
      }
    }
  }

  internal fun getDefaultScaledFontSize(): TerminalFontSize {
    return TerminalFontSettingsService.getInstance().getSettings().fontSize.scale()
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalFontSizeProvider.Listener) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): TerminalFontSizeProviderImpl = service()
  }
}