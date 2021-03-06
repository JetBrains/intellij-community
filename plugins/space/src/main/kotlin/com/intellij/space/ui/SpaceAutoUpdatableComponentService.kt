// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.delay
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.SequentialLifetimes
import javax.swing.JComponent

@Service
internal class SpaceAutoUpdatableComponentService : LifetimedDisposable by LifetimedDisposableImpl() {
  companion object {
    private const val DELAY = 60_000

    fun createAutoUpdatableComponent(componentFactory: () -> JComponent) =
      service<SpaceAutoUpdatableComponentService>().createAutoUpdatableComponentImpl(componentFactory)
  }

  private val autoUpdateLifetime = SequentialLifetimes(lifetime)

  private val componentsToUpdate = mutableListOf<ComponentToUpdate>()

  @RequiresEdt
  private fun createAutoUpdatableComponentImpl(componentFactory: () -> JComponent): JComponent {
    val container = BorderLayoutPanel().apply {
      isOpaque = false
      add(componentFactory())
    }
    val componentToUpdate = ComponentToUpdate(container, componentFactory)
    return object : Wrapper(container) {
      override fun addNotify() {
        super.addNotify()

        if (componentsToUpdate.isEmpty()) {
          startUpdating()
        }
        if (componentToUpdate !in componentsToUpdate) {
          componentsToUpdate.add(componentToUpdate)
        }
      }

      override fun removeNotify() {
        super.removeNotify()

        componentsToUpdate.remove(componentToUpdate)
        if (componentsToUpdate.isEmpty()) {
          stopUpdating()
        }
      }
    }
  }

  private fun startUpdating() {
    launch(autoUpdateLifetime.next(), Ui) {
      while (true) {
        delay(DELAY)
        updateComponents()
      }
    }
  }

  private fun stopUpdating() {
    autoUpdateLifetime.clear()
  }

  private fun updateComponents() {
    componentsToUpdate.forEach { (container, factory) ->
      container.removeAll()
      container.addToCenter(factory())
      container.revalidate()
      container.repaint()
    }
  }

  private data class ComponentToUpdate(val container: BorderLayoutPanel, val componentFactory: () -> JComponent)
}