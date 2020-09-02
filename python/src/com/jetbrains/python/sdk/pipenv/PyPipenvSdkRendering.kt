// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PySdkRenderingExtension
import javax.swing.Icon

class PyPipenvSdkRendering : PySdkRenderingExtension {

  override fun getAdditionalData(sdk: Sdk): String? = if (sdk.isPipEnv) sdk.versionString else null

  override fun getIcon(sdk: Sdk): Icon? = if (sdk.isPipEnv) PIPENV_ICON else null
}