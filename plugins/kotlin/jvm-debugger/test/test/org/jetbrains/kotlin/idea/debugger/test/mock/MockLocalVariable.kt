// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

// LocalVariableImpl.<init> is package private in com.jetbrains.jdi, but the code in
// the debugger relies on the LocalVariableImpl.getBorder methods. In order to test
// this code we have to add a MockLocalVariable that inherits from LocalVariableImpl
// and that's why this code is in the com.jetbrains.jdi package.
package com.jetbrains.jdi

import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.test.mock.MockLocation
import org.jetbrains.kotlin.idea.debugger.test.mock.MockMethod

class MockLocalVariable(
    val startPc: Int,
    val length: Int,
    name: String,
    descriptor: String,
    slot: Int,
    scopeStart: MockLocation,
    scopeEnd: MockLocation,
    method: MockMethod
): LocalVariableImpl(
    // Passing mockMethod.virtualMachine() won't work, since the code will attempt to cast
    // it to com.jetbrains.jdi.VirtualMachineImpl
    null,
    method,
    slot,
    scopeStart,
    scopeEnd,
    name,
    descriptor,
    null
) {
    private val virtualMachine = method.virtualMachine()
    override fun virtualMachine(): VirtualMachine = virtualMachine

    // [LocalVariableImpl.isVisible] checks that the `vm` is non-null.
    // That's the only reason we have to override it.
    override fun isVisible(frame: StackFrame): Boolean =
        frame.location().codeIndex().toInt() in startPc until startPc + length

    override fun compareTo(other: LocalVariable): Int {
        error("`LocalVariable.compareTo` does not provide a stable ordering and should not be used.")
    }

    override fun isArgument(): Boolean {
        error("`LocalVariable.isArgument` does not work on dex and should not be used.")
    }
}
