// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.k2.configurations.DefaultKotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.KotlinScriptResolutionService
import org.jetbrains.kotlin.idea.core.script.v1.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile

class K2KotlinScratchFile(project: Project, virtualFile: VirtualFile, val coroutineScope: CoroutineScope) :
    ScratchFile(project, virtualFile) {
    val executor: K2ScratchExecutor = K2ScratchExecutor(this, project, coroutineScope)

    override fun setModule(module: Module?) {
        ScriptRelatedModuleNameFile[project, virtualFile] = module?.name

        coroutineScope.launch {
            DefaultKotlinScriptEntityProvider.getInstance(project).removeKotlinScriptEntity(virtualFile)
            KotlinScriptResolutionService.getInstance(project).process(virtualFile)
        }
    }
}