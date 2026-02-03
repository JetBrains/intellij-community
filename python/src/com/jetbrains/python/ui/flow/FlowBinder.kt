// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui.flow

import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.text
import com.intellij.util.ui.showingScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JToggleButton
import javax.swing.text.JTextComponent

/**
 * Binds field to the [Flow] making field to show any string from this flow as long as this component is displayed.
 * ```kotlin
 *  val f:Flow<String>
 *  textField().bindText(f)
 * ```
 */
@ApiStatus.Experimental
fun Cell<JTextComponent>.bindText(flow: Flow<String>) = apply {
  bindImpl(flow, { text(it) })
}

/**
 * Binds field to a [MutableStateFlow] making field to show any string from this flow as long as this component is displayed and vice versa.
 * ```kotlin
 *  val f:MutableStateFlow<String>
 *  textField().bindText(f)
 * ```
 */
@ApiStatus.Experimental
fun Cell<JTextComponent>.bindText(flow: MutableStateFlow<String>) = apply {
  bindImpl(flow, { text(it) }, Pair({ component.text }, flow))
}

/**
 * Binds checkbox to a [Flow] making changing its `selected` state on each event
 * ```kotlin
 *  val f:Flow<Boolean>
 *  checkbox().bindSelected(f)
 * ```
 */
@ApiStatus.Experimental
fun Cell<JToggleButton>.bindSelected(flow: Flow<Boolean>) = apply {
  bindImpl(flow, { selected(it) })
}

/**
 * Binds checkbox to the [MutableStateFlow] making changing its `selected` state on each event and vice versa
 * ```kotlin
 *  val f:MutableState<Boolean>
 *  checkbox().bindSelected(f)
 * ```
 */
@ApiStatus.Experimental
fun Cell<JToggleButton>.bindSelected(flow: MutableStateFlow<Boolean>) = apply {
  bindImpl(flow, { selected(it) }, Pair({ component.isSelected }, flow))
}

//// impl

private fun <D, C : JComponent> Cell<C>.bindImpl(
  flow: Flow<D>,
  flowToComponent: (D) -> Unit,
  setter: Pair<(() -> D), MutableStateFlow<D>>? = null,
) {
  if (setter != null) {
    onChanged { setter.second.value = setter.first() }
  }
  component.showingScope("..") {
    flow.collect {
      flowToComponent(it)
    }
  }
}