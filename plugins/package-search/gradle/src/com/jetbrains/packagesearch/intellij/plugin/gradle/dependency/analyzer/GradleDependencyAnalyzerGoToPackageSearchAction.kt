package com.jetbrains.packagesearch.intellij.plugin.gradle.dependency.analyzer

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.module.Module
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.jetbrains.packagesearch.intellij.plugin.dependency.analyzer.DependencyAnalyzerGoToPackageSearchAction
import org.jetbrains.plugins.gradle.dependency.analyzer.getParentModule
import org.jetbrains.plugins.gradle.dependency.analyzer.getUnifiedCoordinates
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID

class GradleDependencyAnalyzerGoToPackageSearchAction : DependencyAnalyzerGoToPackageSearchAction() {

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