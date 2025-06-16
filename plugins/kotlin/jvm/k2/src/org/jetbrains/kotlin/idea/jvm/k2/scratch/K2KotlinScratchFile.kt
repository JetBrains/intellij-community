// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileListener
import org.jetbrains.kotlin.idea.jvm.shared.scratch.syncPublisherWithDisposeCheck

class K2KotlinScratchFile(project: Project, file: VirtualFile, coroutineScope: CoroutineScope) : ScratchFile(project, file) {
    val executor: K2ScratchExecutor = K2ScratchExecutor(this, project, coroutineScope)

    init {
        project.syncPublisherWithDisposeCheck(ScratchFileListener.TOPIC).fileCreated(this)
    }
}