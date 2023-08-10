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

package com.jetbrains.packagesearch.intellij.plugin.maven.dependency.analyzer

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.intellij.plugin.dependency.analyzer.DependencyAnalyzerGoToPackageSearchAction
import org.jetbrains.idea.maven.project.actions.getParentModule
import org.jetbrains.idea.maven.project.actions.getUnifiedCoordinates
import org.jetbrains.idea.maven.utils.MavenUtil.SYSTEM_ID

class MavenDependencyAnalyzerGoToPackageSearchAction : DependencyAnalyzerGoToPackageSearchAction() {

    override val systemId: ProjectSystemId = SYSTEM_ID

    override fun getModule(e: AnActionEvent): Module? {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return null
        val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY) ?: return null
        return getParentModule(project, dependency)
    }

    override fun getUnifiedCoordinates(e: AnActionEvent): UnifiedCoordinates? {
        val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY) ?: return null
        return getUnifiedCoordinates(dependency)
    }
}
