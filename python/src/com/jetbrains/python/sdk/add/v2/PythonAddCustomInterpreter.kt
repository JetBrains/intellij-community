// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.sdk.add.v2.PythonInterpreterCreationTargets.LOCAL_MACHINE

class PythonAddCustomInterpreter(private val settings: PythonAddInterpreterState) {

  //private lateinit var targetSelector: ComboBox<PythonInterpreterCreationTargets>

  private val targets = mapOf(
    LOCAL_MACHINE to PythonLocalEnvironmentCreator(settings),
  )

  fun buildPanel(outerPanel: Panel) {
    with(outerPanel) {

      // todo uncomment for all available targets
      //row(message("sdk.create.custom.develop.on")) {
      //  targetSelector = comboBox(targets.keys, PythonEnvironmentComboBoxRenderer())
      //    .widthGroup("env_aligned")
      //    .component
      //}
      //targets.forEach { target ->
      //  rowsRange {
      //    target.value.buildPanel(this)
      //  }.visibleIf(targetSelector.selectedValueMatches { it == target.key })
      //}


      rowsRange {
        targets[LOCAL_MACHINE]!!.buildPanel(this)
      }
    }
  }

  fun onShown() {
    targets.values.forEach(PythonLocalEnvironmentCreator::onShown)
  }

  fun getSdk(): Sdk {
    // todo uncomment for all available targets
    //return targets[targetSelector.selectedItem]!!.getSdk()
    return targets[LOCAL_MACHINE]!!.getSdk()
  }

}