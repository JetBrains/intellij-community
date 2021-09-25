// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.kotlin.jps.incremental.CacheAttributesDiff
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import java.io.File

/**
 * Used for assertions in tests.
 */
interface TestingBuildLogger {
    fun invalidOrUnusedCache(chunk: KotlinChunk?, target: KotlinModuleBuildTarget<*>?, attributesDiff: CacheAttributesDiff<*>) = Unit
    fun chunkBuildStarted(context: CompileContext, chunk: org.jetbrains.jps.ModuleChunk) = Unit
    fun afterChunkBuildStarted(context: CompileContext, chunk: org.jetbrains.jps.ModuleChunk) = Unit
    fun compilingFiles(files: Collection<File>, allRemovedFilesFiles: Collection<File>) = Unit
    fun addCustomMessage(message: String) = Unit
    fun buildFinished(exitCode: ModuleLevelBuilder.ExitCode) = Unit
    fun markedAsDirtyBeforeRound(files: Iterable<File>) = Unit
    fun markedAsDirtyAfterRound(files: Iterable<File>) = Unit
}
