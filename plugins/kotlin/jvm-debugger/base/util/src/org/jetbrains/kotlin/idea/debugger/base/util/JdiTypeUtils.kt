// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("JdiTypeUtils")

package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.debugger.engine.DebuggerUtils
import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType

fun ClassType.hasSuperClass(jdiName: String): Boolean {
    var current = this
    while (true) {
        if (current.name() == jdiName) {
            return true
        }
        current = current.superclass() ?: return false
    }
}

fun ClassType.hasInterface(jdiName: String): Boolean {
    return allInterfaces().any { it.name() == jdiName }
}

fun ReferenceType.findMethod(name: String, methodSignature: String?): Method {
    return DebuggerUtils.findMethod(this, name, methodSignature) ?: error("Method {$name} {$methodSignature} not found")
}
