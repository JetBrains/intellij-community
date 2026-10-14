// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import org.jetbrains.java.debugger.breakpoints.JavaBreakpointFiltersPanel

class KotlinBreakpointFiltersPanel(project: Project) :
    JavaBreakpointFiltersPanel<KotlinPropertyBreakpointProperties, XLineBreakpoint<KotlinPropertyBreakpointProperties>>(project)
