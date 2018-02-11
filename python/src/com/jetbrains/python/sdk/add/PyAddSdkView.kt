// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import java.awt.Component
import javax.swing.Icon

/**
 * Represents the view for adding new Python SDK. It is used in
 * [PyAddSdkDialog].
 */
interface PyAddSdkView {
  val panelName: String

  val icon: Icon

  fun getOrCreateSdk(): Sdk?

  fun onSelected()

  /**
   * [PyAddSdkStateListener.onActionsStateChanged] is called after changes in
   * [actions].
   */
  val actions: Map<PyAddSdkDialogFlowAction, Boolean>

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

  /**
   * Creates SDK and returns it.
   *
   * If some error occurs an [Exception] is thrown.
   *
   * @throws Exception if SDK creation failed for some reason
   */
  fun finish(): Sdk

  /**
   * Returns the list of validation errors. The returned list is empty if there
   * are no errors found.
   *
   * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
   */
  fun validateAll(): List<ValidationInfo>

  fun addStateListener(stateListener: PyAddSdkStateListener)
}