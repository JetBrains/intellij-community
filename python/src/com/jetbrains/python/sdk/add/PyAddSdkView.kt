// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.jetbrains.python.sdk.add.wizard.WizardStep
import javax.swing.Icon

/**
 * Represents the view for adding new Python SDK. It is used in
 * [PyAddSdkDialog].
 */
interface PyAddSdkView {
  val panelName: String

  val icon: Icon

  fun getOrCreateSdk(): Sdk?

  /**
   * Returns the list of validation errors. The returned list is empty if there
   * are no errors found.
   *
   * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
   */
  fun validateAll(): List<ValidationInfo>

  /**
   * Returns the first wizard step or `null` if this is not a wizard.
   */
  // TODO refactor me!
  fun getFirstWizardStep(): WizardStep<Sdk?>
}