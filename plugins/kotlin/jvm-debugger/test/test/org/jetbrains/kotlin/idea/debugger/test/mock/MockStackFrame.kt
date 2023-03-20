// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.mock

import com.sun.jdi.*

// A [StackFrame] that wraps a location, but does not allow any value access.
class MockStackFrame(private val location: Location) : StackFrame {
    override fun location(): Location = location

    override fun virtualMachine(): VirtualMachine = location.virtualMachine()

    override fun visibleVariables(): MutableList<LocalVariable> {
        error("StackFrame.visibleVariables is Java specific and should not be used for Kotlin.")
    }

    override fun visibleVariableByName(name: String): LocalVariable {
        error("StackFrame.visibleVariableByName is Java specific and should not be used for Kotlin.")
    }

    override fun getArgumentValues(): MutableList<Value> {
        error("StackFrame.getArgumentValues does not work on a dex VM and should not be used.")
    }

    override fun thread(): ThreadReference { TODO("Not yet implemented") }
    override fun thisObject(): ObjectReference? { TODO("Not yet implemented") }
    override fun getValue(variable: LocalVariable?): Value { TODO("Not yet implemented") }
    override fun getValues(variables: List<LocalVariable>?): MutableMap<LocalVariable, Value> { TODO("Not yet implemented") }
    override fun setValue(variable: LocalVariable?, value: Value?) { TODO("Not yet implemented") }
}
