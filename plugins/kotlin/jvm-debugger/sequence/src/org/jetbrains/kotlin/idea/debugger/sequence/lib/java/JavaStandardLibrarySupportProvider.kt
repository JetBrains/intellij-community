// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.lib.java

import com.intellij.debugger.streams.core.lib.LibrarySupport
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.core.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder
import com.intellij.debugger.streams.lib.impl.JvmLibrarySupportProvider
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupport
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.debugger.sequence.psi.impl.KotlinChainTransformerImpl
import org.jetbrains.kotlin.idea.debugger.sequence.psi.impl.PackageBasedCallChecker
import org.jetbrains.kotlin.idea.debugger.sequence.psi.impl.TerminatedChainBuilder
import org.jetbrains.kotlin.idea.debugger.sequence.psi.java.JavaStreamChainTypeExtractor
import org.jetbrains.kotlin.idea.debugger.sequence.psi.java.StandardLibraryCallChecker
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.JavaPeekCallFactory
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinStatementFactory
import org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.KotlinTraceExpressionBuilder

class JavaStandardLibrarySupportProvider : JvmLibrarySupportProvider() {
    private val builder: TerminatedChainBuilder = TerminatedChainBuilder(
        KotlinChainTransformerImpl(JavaStreamChainTypeExtractor()),
        StandardLibraryCallChecker(PackageBasedCallChecker("java.util.stream"))
    )
    private val support: StandardLibrarySupport by lazy { StandardLibrarySupport() }
    private val dsl: DslImpl by lazy { DslImpl(KotlinStatementFactory(JavaPeekCallFactory())) }

    override fun getLanguageId(): String = KotlinLanguage.INSTANCE.id

    override fun getChainBuilder(): StreamChainBuilder = builder

    override fun getLibrarySupport(): LibrarySupport = support

    override fun getExpressionBuilder(project: Project): TraceExpressionBuilder =
        KotlinTraceExpressionBuilder(dsl, support.createHandlerFactory(dsl))
}
