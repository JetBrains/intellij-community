package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.mpp

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.GradleProjectModuleProvider
import org.jetbrains.kotlin.idea.caches.project.isMPPModule
import org.jetbrains.kotlin.idea.util.projectStructure.allModules

class GradleMppProjectModuleProvider : GradleProjectModuleProvider() {

    override fun obtainAllProjectModulesFor(project: Project): Sequence<ProjectModule> =
        super.obtainAllProjectModulesFor(project)
            .filter {
                it.nativeModule.isMPPParent(project) // for child modules: it.nativeModule.isMPPModule
            }
            .map { it.copy(moduleType = GradleMppProjectModuleType) }
}

private fun Module.isMPPParent(project: Project): Boolean {
    // Dirty hack to see whether the current module is an MPP parent.
    //
    // Proper way would be to query Gradle properties, then find the "kotlin" property details.
    // isMppParent = prj.hasProperty("kotlin") && prj.kotlin.class.simpleName.toLowerCase().contains("multiplatform")
    //
    // However, this does not seem to be exposed. While waiting for team input, going with this workaround:

    val mppModules = project.allModules().filter { it.isMPPModule }.toList()

    return mppModules.any { it.name.substringBeforeLast('.').endsWith(this.name) }
}
