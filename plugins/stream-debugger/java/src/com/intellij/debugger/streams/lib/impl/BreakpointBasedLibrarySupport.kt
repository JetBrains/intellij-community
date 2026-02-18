// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.LibrarySupport
import com.intellij.debugger.streams.trace.breakpoint.BreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedHandlerFactory

internal interface BreakpointBasedLibrarySupport: LibrarySupport {
  fun createRuntimeHandlerFactory(objectStorage: ObjectStorage): BreakpointBasedHandlerFactory

  val breakpointResolverFactory: BreakpointPositionResolver
}