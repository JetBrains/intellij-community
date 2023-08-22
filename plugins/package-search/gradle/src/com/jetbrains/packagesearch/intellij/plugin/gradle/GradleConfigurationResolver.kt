package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.jetbrains.packagesearch.intellij.plugin.gradle.tooling.GradleConfigurationModelBuilder
import com.jetbrains.packagesearch.intellij.plugin.gradle.tooling.GradleConfigurationReportModel
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class GradleConfigurationResolver : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses(): Set<Class<*>> =
        setOf(GradleConfigurationReportModel::class.java, Unit::class.java)

    override fun getToolingExtensionsClasses(): Set<Class<*>> =
        setOf(GradleConfigurationModelBuilder::class.java, Unit::class.java)

    private inline fun <reified T> IdeaModule.getExtraProject(): T? =
        resolverCtx.getExtraProject(this@getExtraProject, T::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        gradleModule.getExtraProject<GradleConfigurationReportModel>()
            ?.toPublic()
            ?.also { ideModule.createChild(GradleConfigurationReportNodeProcessor.ESM_REPORTS_KEY, it) }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}

