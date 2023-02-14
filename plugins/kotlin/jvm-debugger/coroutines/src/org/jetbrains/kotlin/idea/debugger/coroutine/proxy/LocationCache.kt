// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.jdi.GeneratedLocation
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class LocationCache(val context: DefaultExecutionContext) {
    fun createLocation(stackTraceElement: StackTraceElement): Location {
        val className = stackTraceElement.className
        val type = context.classesCache[className].firstOrNull() ?: error("Unable to find loaded class $className")
        return createLocation(type, stackTraceElement.methodName, stackTraceElement.lineNumber)
    }

    fun createLocation(
        type: ReferenceType,
        methodName: String,
        line: Int
    ): Location {
        if (line >= 0) {
            try {
                val location = DebuggerUtilsAsync.locationsOfLineSync(type, null, null, line).stream()
                        .filter { l: Location -> l.method().name() == methodName }
                        .findFirst().orElse(null)
                if (location != null) {
                    return location
                }
            } catch (ignored: AbsentInformationException) {
            }
        }
        return GeneratedLocation(type, methodName, line)
    }
}