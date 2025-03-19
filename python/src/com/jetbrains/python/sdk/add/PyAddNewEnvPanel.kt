// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode

/**
 * [preferredType] will be selected by UI as a default interpreter type for this panel
 */
abstract class PyAddNewEnvPanel(val preferredType: PythonInterpreterSelectionMode? = null) : PyAddSdkPanel() {
  abstract val envName: String
}