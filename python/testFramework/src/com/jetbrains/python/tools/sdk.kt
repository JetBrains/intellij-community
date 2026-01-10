// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonBinary

fun createSdk(venvPython: PythonBinary): Sdk {
  return SdkConfigurationUtil.setupSdk(emptyArray(),
                                       venvPython.refreshAndFindVirtualFileOrDirectory()!!,
                                       SdkType.findByName(PyNames.PYTHON_SDK_ID_NAME)!!,
                                       null,
                                       null)
}


