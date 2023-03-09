/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.packagesearch.intellij.plugin.toNioPathOrNull
import com.jetbrains.packagesearch.intellij.plugin.util.filesChangedEventFlow
import com.jetbrains.packagesearch.intellij.plugin.util.send
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

open class FileWatcherSignalProvider(private val paths: List<Path>) : FlowModuleChangesSignalProvider {

    constructor(vararg paths: Path) : this(paths.toList())

    private val absolutePathStrings = paths.map { it.absolutePathString() }

    override fun registerModuleChangesListener(project: Project): Flow<Unit> {
        val localFs: LocalFileSystem = LocalFileSystem.getInstance()
        val watchRequests = absolutePathStrings.asSequence()
            .onEach { localFs.findFileByPath(it) }
            .mapNotNull { localFs.addRootToWatch(it, false) }
        return channelFlow {
            project.filesChangedEventFlow.flatMapMerge { it.asFlow() }
                .filter { vFileEvent -> paths.any { it.name == vFileEvent.file?.name } } // check the file name before resolving the absolute path string
                .filter { vFileEvent -> absolutePathStrings.any { it == vFileEvent.file?.toNioPathOrNull()?.absolutePathString() } }
                .onEach { send() }
                .launchIn(this)
            awaitClose { watchRequests.forEach { request -> localFs.removeWatchedRoot(request) } }
        }
    }
}

