// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.jvm.shared.scratch

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.parsing.KotlinParserDefinition.Companion.STD_SCRIPT_SUFFIX

class ScratchFileModuleInfoProvider : ScratchFileListener {
    companion object {
        private val LOG = logger<ScratchFileModuleInfoProvider>()
    }

    override fun fileCreated(file: ScratchFile) {
        val ktFile = file.ktFile ?: return
        val virtualFile = ktFile.virtualFile ?: return
        val project = ktFile.project

        if (virtualFile.extension != STD_SCRIPT_SUFFIX) {
            LOG.error("Kotlin Scratch file should have .kts extension. Cannot add scratch panel for ${virtualFile.path}")
            return
        }

        file.addModuleListener { psiFile, module ->
            ScriptRelatedModuleNameFile[project, psiFile.virtualFile] = module?.name

            // Drop caches for old module
            ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            // Force re-highlighting
            runReadAction { DaemonCodeAnalyzer.getInstance(project).restart(psiFile) }
        }

        if (virtualFile.isKotlinWorksheet) {
            val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: return
            file.setModule(module)
        } else {
            val module = ScriptRelatedModuleNameFile[project, virtualFile]?.let { ModuleManager.getInstance(project).findModuleByName(it) } ?: return
            file.setModule(module)
        }
    }
}
