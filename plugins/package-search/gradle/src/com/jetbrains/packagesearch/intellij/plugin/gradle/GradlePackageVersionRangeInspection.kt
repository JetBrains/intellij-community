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

package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageVersionRangeInspection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType

internal class GradlePackageVersionRangeInspection : PackageVersionRangeInspection() {

    companion object {

        private const val FILE_TYPE_GROOVY = "groovy"
        private const val FILE_TYPE_KOTLIN = "kotlin"
        private const val EXTENSION_GRADLE = "gradle"
        private const val EXTENSION_GRADLE_KTS = "gradle.kts"

        fun hasSupportFor(psiFile: PsiFile?): Boolean {
            // Logic based on com.android.tools.idea.gradle.project.sync.GradleFiles.isGradleFile()
            val file = psiFile?.virtualFile ?: return false

            val isGroovyFile = FILE_TYPE_GROOVY.equals(psiFile.fileType.name, ignoreCase = true)
            val isKotlinFile = FILE_TYPE_KOTLIN.equals(psiFile.fileType.name, ignoreCase = true)

            if (!isGroovyFile && !isKotlinFile) return false
            return file.name.endsWith(EXTENSION_GRADLE, ignoreCase = true) || file.name.endsWith(EXTENSION_GRADLE_KTS, ignoreCase = true)
        }

        fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
            projectModuleType is GradleProjectModuleType
    }

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.range.description.gradle")

    override fun selectPsiElementIndex(dependencyDeclarationIndexes: DependencyDeclarationIndexes) =
        dependencyDeclarationIndexes.coordinatesStartIndex

    override fun shouldCheckFile(file: PsiFile) = hasSupportFor(file)
}
