// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.impl.projectPath

import com.intellij.ui.TextAccessor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Emits changes in [component] to the [flow] with [debounceDelay]
 */
internal class DocumentListenerToFlowAdapter(private val component: TextAccessor, debounceDelay: Duration = 10.milliseconds) : DocumentListener {
  private val _flow = MutableStateFlow<String>(component.text)

  @OptIn(FlowPreview::class)
  val flow: Flow<String> = _flow.debounce(debounceDelay)
  override fun insertUpdate(e: DocumentEvent?) {
    update()
  }

  override fun removeUpdate(e: DocumentEvent?) {
    update()
  }

  override fun changedUpdate(e: DocumentEvent) = Unit

  private fun update() {
    _flow.value = component.text
  }
}