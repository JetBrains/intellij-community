// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.wizard

import com.intellij.openapi.ui.ValidationInfo
import java.awt.Component

/**
 * Stateful wizard view.
 */
interface WizardView<out T> {
  /**
   * [WizardControlsListener.onStateChanged] is called right after these changes.
   */
  val actions: Map<WizardControlAction, Boolean>

  /**
   * The [component] *might* return the new [Component] after [next] or
   * [previous].
   */
  val component: Component

  /**
   * @throws IllegalStateException
   */
  fun previous()

  /**
   * @throws IllegalStateException
   */
  fun next()

  fun finish(): T

  /**
   * Returns the list of validation errors. The returned list is empty if there
   * are no errors found.
   *
   * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
   */
  fun validateAll(): List<ValidationInfo>

  fun addStateListener(stateListener: WizardStateListener)

  fun addControlListener(listener: WizardControlsListener)
}