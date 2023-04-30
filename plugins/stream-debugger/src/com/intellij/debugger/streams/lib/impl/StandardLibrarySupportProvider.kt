// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.LibrarySupport
import com.intellij.debugger.streams.lib.LibrarySupportProvider
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder
import com.intellij.debugger.streams.psi.impl.PackageChainDetector
import com.intellij.debugger.streams.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.trace.breakpoint.JavaBreakpointResolver
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.BreakpointResolverFactory
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.BreakpointTracingSupport
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeHandlerFactory
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl.DefaultRuntimeHandlerFactory
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory
import com.intellij.debugger.streams.trace.impl.JavaTraceExpressionBuilder
import com.intellij.debugger.streams.wrapper.StreamChainBuilder
import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
internal class StandardLibrarySupportProvider : LibrarySupportProvider {
  private companion object {
    val builder: StreamChainBuilder = JavaStreamChainBuilder(JavaChainTransformerImpl(),
                                                             PackageChainDetector.forJavaStreams("java.util.stream"))
    val support: LibrarySupport = StandardLibrarySupport()
    val dsl: Dsl = DslImpl(JavaStatementFactory())
  }

  override fun getLanguageId(): String = "JAVA"

  override fun getExpressionBuilder(project: Project): TraceExpressionBuilder =
    JavaTraceExpressionBuilder(project, support.createHandlerFactory(dsl), dsl)

  override fun getChainBuilder(): StreamChainBuilder = builder

  override fun getLibrarySupport(): LibrarySupport = support

  override fun getBreakpointTracingSupport(): BreakpointTracingSupport = object : BreakpointTracingSupport {
    override fun createRuntimeHandlerFactory(valueManager: ValueManager): RuntimeHandlerFactory {
      return DefaultRuntimeHandlerFactory(valueManager)
    }

    override val breakpointResolverFactory: BreakpointResolverFactory = BreakpointResolverFactory {
      JavaBreakpointResolver(it)
    }
  }
}
