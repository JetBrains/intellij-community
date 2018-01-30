// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.wizard

import com.intellij.openapi.ui.ValidationInfo
import java.awt.Component

/**
 * @param T the result type of the object created by Wizard
 */
interface WizardStep<T> {
  val component: Component

  fun hasNext(): Boolean

  /**
   * Navigates to the next wizard step. [validate] method should be called
   * prior to [next] to validate the current state.
   *
   * @throws IllegalStateException if next step does not exist and [hasNext]
   * returns `false`
   */
  fun next(): WizardStep<T>

  fun hasPrevious(): Boolean

  /**
   * @throws IllegalStateException if previous step does not exist
   */
  fun previous(): WizardStep<T>

  /**
   * Returns the list of validation errors. The returned list is empty if no
   * validation errors found.
   *
   * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
   */
  fun validateAll(): List<ValidationInfo>

  fun finish(): T
}