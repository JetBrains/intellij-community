/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.scratch

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.ScriptRelatedModuleNameFile
import org.jetbrains.kotlin.idea.util.projectStructure.getModule
import org.jetbrains.kotlin.parsing.KotlinParserDefinition.Companion.STD_SCRIPT_SUFFIX
import org.jetbrains.kotlin.psi.KtFile


class ScratchFileModuleInfoProvider : StartupActivity {
    private val LOG = Logger.getInstance(this.javaClass)

    override fun runActivity(project: Project) {
        project.messageBus.connect().subscribe(ScratchFileListener.TOPIC, object : ScratchFileListener {
            override fun fileCreated(file: ScratchFile) {
                val ktFile = file.getPsiFile() as? KtFile ?: return
                val virtualFile = ktFile.virtualFile ?: return

                if (virtualFile.extension != STD_SCRIPT_SUFFIX) {
                    LOG.error("Kotlin Scratch file should have .kts extension. Cannot add scratch panel for ${virtualFile.path}")
                    return
                }

                file.addModuleListener { psiFile, module ->
                    ScriptRelatedModuleNameFile[project, psiFile.virtualFile] = module?.name

                    // Drop caches for old module
                    ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
                    // Force re-highlighting
                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                }

                if (virtualFile.isKotlinWorksheet) {
                    val module = virtualFile.getModule(project) ?: return
                    file.setModule(module)
                } else {
                    val module = ScriptRelatedModuleNameFile[project, virtualFile]?.let { ModuleManager.getInstance(project).findModuleByName(it) } ?: return
                    file.setModule(module)
                }
            }
        })
    }
}
