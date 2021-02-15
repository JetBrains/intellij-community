// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.ui

import circlet.platform.client.XPagedListOnFlux
import com.intellij.ui.components.JBList
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.delay
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.Property
import runtime.reactive.SequentialLifetimes
import java.awt.event.AdjustmentEvent
import javax.swing.JScrollPane


fun <T> bindScroll(
  lifetime: Lifetime,
  scrollableList: JScrollPane,
  vm: LoadableListVm,
  list: JBList<out T>
) {
  val slVisibility = SequentialLifetimes(lifetime)

  fun updateScroll(force: Boolean) {
    if (!lifetime.isTerminated) {
      // run element visibility updater, tracks elements in a view port and set visible to true.
      launch(slVisibility.next(), Ui) {
        delay(300)
        while (vm.isLoading.value) {
          delay(300)
        }
      }
      val last = list.lastVisibleIndex
      if (force || !vm.isLoading.value) {
        vm.xList.value?.let { value ->
          if ((last == -1 || list.model.size < last + 10) && value.hasMore()) {
            launch(lifetime, Ui) {
              value.more()
              updateScroll(true)
            }
          }
        }
      }
    }
  }

  val listener: (e: AdjustmentEvent) -> Unit = {
    updateScroll(false)
  }

  scrollableList.verticalScrollBar.addAdjustmentListener(listener)
  lifetime.add {
    scrollableList.verticalScrollBar.removeAdjustmentListener(listener)
  }
}

interface LoadableListVm {
  val isLoading: Property<Boolean>

  val xList: Property<LoadableListAdapter?>
}

data class LoadableListVmImpl(
  override val isLoading: Property<Boolean>,
  override val xList: Property<LoadableListAdapter?>
) : LoadableListVm

interface LoadableListAdapter {
  fun hasMore(): Boolean

  suspend fun more()
}

fun Property<XPagedListOnFlux<*>?>.toLoadable(): Property<LoadableListAdapter?> {
  val lla = object : LoadableListAdapter {
    override fun hasMore(): Boolean {
      return this@toLoadable.value?.hasMore?.value ?: false
    }

    override suspend fun more() {
      this@toLoadable.value?.more()
    }
  }
  return Property.create(lla)
}


