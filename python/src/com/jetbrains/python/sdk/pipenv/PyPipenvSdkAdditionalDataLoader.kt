// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.sdk.PySdkAdditionalDataLoader
import com.jetbrains.python.sdk.pipenv.PyPipEnvSdkAdditionalData.Companion.load
import org.jdom.Element

class PyPipenvSdkAdditionalDataLoader : PySdkAdditionalDataLoader {
  override fun loadForSdk(element: Element): SdkAdditionalData? = load(element)
}