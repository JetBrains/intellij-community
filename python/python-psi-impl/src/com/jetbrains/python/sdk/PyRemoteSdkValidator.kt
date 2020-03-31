// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

/**
 * this extension point is temporary and it's not meant for public use!
 */
interface PyRemoteSdkValidator {
  companion object {
    private val EP = ExtensionPointName.create<PyRemoteSdkValidator>("Pythonid.remoteSdkValidator")
    fun isInvalid(sdk: Sdk) = EP.extensions.any { it.isInvalid(sdk) }
  }

  fun isInvalid(sdk: Sdk): Boolean
}