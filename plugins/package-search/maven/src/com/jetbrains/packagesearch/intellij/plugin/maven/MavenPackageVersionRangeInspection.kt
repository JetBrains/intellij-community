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

package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageVersionRangeInspection
import org.jetbrains.idea.maven.utils.MavenUtil

internal class MavenPackageVersionRangeInspection : PackageVersionRangeInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.range.description.maven")
    override fun selectPsiElementIndex(dependencyDeclarationIndexes: DependencyDeclarationIndexes) =
        dependencyDeclarationIndexes.versionStartIndex

    override fun shouldCheckFile(file: PsiFile) = MavenUtil.isPomFile(file.project, file.virtualFile)
}
