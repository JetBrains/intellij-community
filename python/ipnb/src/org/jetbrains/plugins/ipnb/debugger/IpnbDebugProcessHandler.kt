// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.execution.process.ProcessHandler
import java.io.OutputStream

class IpnbDebugProcessHandler() : ProcessHandler() {

  override fun destroyProcessImpl() {

  }

  override fun detachProcessImpl() {

  }

  override fun detachIsDefault() = false

  override fun getProcessInput(): OutputStream? {
    return null
  }

}