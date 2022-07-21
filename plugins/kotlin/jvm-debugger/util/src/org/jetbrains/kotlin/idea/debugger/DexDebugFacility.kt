// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.sun.jdi.VirtualMachine

object DexDebugFacility {
    fun isDex(virtualMachine: VirtualMachine): Boolean {
        return virtualMachine.name() == "Dalvik"
    }

    fun isDex(debugProcess: DebugProcess): Boolean {
        val virtualMachineProxy = debugProcess.virtualMachineProxy as? VirtualMachineProxyImpl ?: return false
        return isDex(virtualMachineProxy.virtualMachine)
    }
}