package com.intellij.space.ui

import circlet.platform.client.XPagedListOnFlux
import com.intellij.ui.components.JBList
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.delay
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.MutableProperty
import runtime.reactive.Property
import runtime.reactive.SequentialLifetimes
import java.awt.event.AdjustmentEvent
import javax.swing.JScrollPane


fun <T> bindScroll(lifetime: Lifetime,
                   scrollableList: JScrollPane,
                   vm: LoadableListVm,
                   list: JBList<out T>
) {
    val slVisibility = SequentialLifetimes(lifetime)

    lateinit var scrollUpdater: (force: Boolean) -> Unit

    scrollUpdater = { force ->
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
                        vm.isLoading.value = true
                        launch(lifetime, Ui) {
                            value.more()
                            scrollUpdater(true)
                        }
                    } else {
                        vm.isLoading.value = false
                    }
                }
            }
        }
    }
    val listener: (e: AdjustmentEvent) -> Unit = {
        scrollUpdater(false)
    }

    scrollableList.verticalScrollBar.addAdjustmentListener(listener)
    lifetime.add {
        scrollableList.verticalScrollBar.removeAdjustmentListener(listener)
    }
}

interface LoadableListVm {
    val isLoading: MutableProperty<Boolean>

    val xList: Property<LoadableListAdapter?>
}

data class LoadableListVmImpl(
    override val isLoading: MutableProperty<Boolean>,
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


