// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaBreakpointHandler
import com.intellij.debugger.engine.JavaBreakpointHandlerFactory
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinFieldBreakpointType
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinLineBreakpointType

class KotlinFieldBreakpointHandlerFactory : JavaBreakpointHandlerFactory {
    override fun createHandler(process: DebugProcessImpl): JavaBreakpointHandler {
        return KotlinFieldBreakpointHandler(process)
    }
}

class KotlinLineBreakpointHandlerFactory : JavaBreakpointHandlerFactory {
    override fun createHandler(process: DebugProcessImpl): JavaBreakpointHandler {
        return KotlinLineBreakpointHandler(process)
    }
}

class KotlinFunctionBreakpointHandlerFactory : JavaBreakpointHandlerFactory {
    override fun createHandler(process: DebugProcessImpl): JavaBreakpointHandler {
        return KotlinFunctionBreakpointHandler(process)
    }
}

class KotlinFunctionBreakpointHandler(process: DebugProcessImpl) : JavaBreakpointHandler(KotlinFunctionBreakpointType::class.java, process)
class KotlinFieldBreakpointHandler(process: DebugProcessImpl) : JavaBreakpointHandler(KotlinFieldBreakpointType::class.java, process)
class KotlinLineBreakpointHandler(process: DebugProcessImpl) : JavaBreakpointHandler(KotlinLineBreakpointType::class.java, process)
