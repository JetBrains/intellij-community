// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.add.wizard.WizardView
import javax.swing.Icon

/**
 * Represents the view for adding new Python SDK. It is used in
 * [PyAddSdkDialog].
 */
interface PyAddSdkView : WizardView<Sdk> {
  val panelName: String

  val icon: Icon

  fun getOrCreateSdk(): Sdk?
}