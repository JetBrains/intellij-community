// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.listeners

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.Disposer
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class MinimapUiListeners(
  parentDisposable: Disposable,
  private val container: JComponent,
  private val contentComponent: JComponent,
  private val updateParameters: () -> Unit,
  private val revalidate: () -> Unit,
  private val repaint: () -> Unit
) {
  private val appConnection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)

  private val componentListener = object : ComponentAdapter() {
    private var lastHeight = -1

    override fun componentResized(componentEvent: ComponentEvent?) {
      if (lastHeight == container.height) {
        return
      }
      lastHeight = container.height
      updateParameters()
      revalidate()
      repaint()
    }
  }

  private val contentComponentListener = object : ComponentAdapter() {
    override fun componentResized(componentEvent: ComponentEvent?) {
      updateParameters()
      repaint()
    }
  }

  private val lafManagerListener = LafManagerListener {
    onAppearanceChanged()
  }

  private val editorColorsListener = EditorColorsListener { _ ->
    onAppearanceChanged()
  }

  init {
    Disposer.register(parentDisposable) {
      container.removeComponentListener(componentListener)
      contentComponent.removeComponentListener(contentComponentListener)
    }
  }

  fun install() {
    container.addComponentListener(componentListener)
    contentComponent.addComponentListener(contentComponentListener)
    appConnection.subscribe(LafManagerListener.TOPIC, lafManagerListener)
    appConnection.subscribe(EditorColorsManager.TOPIC, editorColorsListener)
  }

  private fun onAppearanceChanged() {
    updateParameters()
    revalidate()
    repaint()
  }
}
