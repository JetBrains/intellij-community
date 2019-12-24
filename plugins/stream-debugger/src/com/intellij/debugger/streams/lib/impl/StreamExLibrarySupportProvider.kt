// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.LibrarySupport
import com.intellij.debugger.streams.lib.LibrarySupportProvider
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder
import com.intellij.debugger.streams.psi.impl.PackageChainDetector
import com.intellij.debugger.streams.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory
import com.intellij.debugger.streams.trace.impl.JavaTraceExpressionBuilder
import com.intellij.debugger.streams.wrapper.StreamChainBuilder
import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
internal class StreamExLibrarySupportProvider : LibrarySupportProvider {
  private val librarySupport = StreamExLibrarySupport()
  private val javaDsl = DslImpl(JavaStatementFactory())
  override fun getLanguageId(): String = "JAVA"

  override fun getLibrarySupport(): LibrarySupport = librarySupport

  override fun getExpressionBuilder(project: Project): TraceExpressionBuilder =
    JavaTraceExpressionBuilder(project, librarySupport.createHandlerFactory(javaDsl), javaDsl)

  override fun getChainBuilder(): StreamChainBuilder =
    JavaStreamChainBuilder(JavaChainTransformerImpl(), PackageChainDetector.forJavaStreams("one.util.streamex"))
}