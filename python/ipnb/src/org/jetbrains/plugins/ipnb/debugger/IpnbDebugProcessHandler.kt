// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes

import java.io.OutputStream

class IpnbDebugProcessHandler : ProcessHandler() {

  override fun destroyProcessImpl() {
    detachProcessImpl()
  }

  override fun detachProcessImpl() {
    notifyProcessTerminated(0)
    notifyTextAvailable("Debugger disconnected.\n", ProcessOutputTypes.SYSTEM)
  }

  override fun detachIsDefault(): Boolean {
    return false
  }

  override fun getProcessInput(): OutputStream? {
    return null
  }
}

