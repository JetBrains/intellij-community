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

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService

abstract class AbstractPackageUpdateInspectionCheck : LocalInspectionTool() {

    protected open fun shouldCheckFile(file: PsiFile): Boolean = false

    protected open fun selectPsiElementIndex(dependencyDeclarationIndexes: DependencyDeclarationIndexes): Int? = null

    final override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
        val isFileNotTracked  = file.virtualFile !in file.project.packageSearchProjectService
                .packageSearchModulesStateFlow
                .value
                .map { it.buildFile }
        val shouldNotCheckFile by lazy { !shouldCheckFile(file) }
        if (isFileNotTracked || shouldNotCheckFile) {
            return emptyArray()
        }

        val fileModule = ModuleUtil.findModuleForFile(file)
        if (fileModule == null) {
            logWarn("Inspecting file $file belonging to an unknown module")
            return emptyArray()
        }

        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        problemsHolder.checkFile(file, fileModule)

        return problemsHolder.resultsArray
    }

    abstract fun ProblemsHolder.checkFile(file: PsiFile, fileModule: Module)
}
