// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.sdk.backend.pythonInterpreter
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.asBinToExecute

/**
 * The eel of an SDK without a target, if it is not the local eel. In the eel native mode, such an SDK can sit on WSL.
 * The debugger and the console open a tunnel to the IDE for such an SDK. The interpreter of the SDK decides where it runs.
 */
@RequiresBackgroundThread
internal fun Sdk.remoteEelOrNull(): EelDescriptor? {
  // An SDK without Python data cannot run, so it has no eel to tunnel to
  if (sdkAdditionalData !is PythonSdkAdditionalData) return null
  return when (val binary = pythonInterpreter().asBinToExecute().successOrNull) {
    is BinOnEel -> binary.path.getEelDescriptor().takeIf { it != LocalEelDescriptor }
    is BinOnTarget, null -> null
  }
}
