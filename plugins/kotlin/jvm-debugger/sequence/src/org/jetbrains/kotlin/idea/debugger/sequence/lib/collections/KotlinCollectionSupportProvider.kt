// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.lib.collections

import com.intellij.debugger.streams.core.lib.LibrarySupport
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.core.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder
import com.intellij.debugger.streams.lib.impl.JvmLibrarySupportProvider
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.debugger.sequence.psi.collections.KotlinCollectionChainBuilder
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinCollectionsPeekCallFactory
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinStatementFactory
import org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.KotlinTraceExpressionBuilder

class KotlinCollectionSupportProvider : JvmLibrarySupportProvider() {
    private val builder: StreamChainBuilder = KotlinCollectionChainBuilder()
    private val support: LibrarySupport by lazy { KotlinCollectionLibrarySupport() }
    private val dsl by lazy { DslImpl(KotlinStatementFactory(KotlinCollectionsPeekCallFactory())) }

    override fun getLanguageId(): String = KotlinLanguage.INSTANCE.id

    override fun getChainBuilder(): StreamChainBuilder = builder

    override fun getLibrarySupport(): LibrarySupport = support

    override fun getExpressionBuilder(project: Project): TraceExpressionBuilder =
        KotlinTraceExpressionBuilder(dsl, support.createHandlerFactory(dsl))
}
