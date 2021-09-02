// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.compilerRunner

import org.jetbrains.kotlin.daemon.client.CompilerCallbackServicesFacadeServer
import org.jetbrains.kotlin.daemon.client.reportFromDaemon
import org.jetbrains.kotlin.daemon.common.JpsCompilerServicesFacade
import org.jetbrains.kotlin.daemon.common.SOCKET_ANY_FREE_PORT
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.InlineConstTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.js.IncrementalDataProvider
import org.jetbrains.kotlin.incremental.js.IncrementalResultsConsumer
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents
import org.jetbrains.kotlin.progress.CompilationCanceledStatus
import java.io.Serializable

internal class JpsCompilerServicesFacadeImpl(
    private val env: JpsCompilerEnvironment,
    port: Int = SOCKET_ANY_FREE_PORT
) : CompilerCallbackServicesFacadeServer(
    env.services[IncrementalCompilationComponents::class.java],
    env.services[LookupTracker::class.java],
    env.services[CompilationCanceledStatus::class.java],
    env.services[ExpectActualTracker::class.java],
    env.services[InlineConstTracker::class.java],
    env.services[IncrementalResultsConsumer::class.java],
    env.services[IncrementalDataProvider::class.java],
    port
), JpsCompilerServicesFacade {

    override fun report(category: Int, severity: Int, message: String?, attachment: Serializable?) {
        env.messageCollector.reportFromDaemon(
            { outFile, srcFiles -> env.outputItemsCollector.add(srcFiles, outFile) },
            category, severity, message, attachment
        )
    }
}