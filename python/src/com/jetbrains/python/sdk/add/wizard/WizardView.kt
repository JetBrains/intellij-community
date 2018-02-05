// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.wizard

import com.intellij.openapi.ui.ValidationInfo
import java.awt.Component

/**
 * Stateful wizard view.
 */
interface WizardView<T> {
  /**
   * The navigation options are changed **only** in EDT.
   * [StateListener.onStateChanged] is called right after these changes.
   */
  val actions: Map<WizardControlAction, Boolean>

  /**
   * The [component] *might* return the new [Component] after [navigate].
   */
  val component: Component

  /**
   * @throws IllegalStateException
   */
  fun navigate(type: WizardControlAction)

  fun finish(): T

  /**
   * Returns the list of validation errors. The returned list is empty if there
   * are no errors found.
   *
   * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
   */
  fun validateAll(): List<ValidationInfo>

  fun reset()

  fun addStateListener(stateListener: StateListener)

  fun addControlListener(listener: WizardControlsListener)

  interface StateListener {
    fun onStateChanged()
  }
}