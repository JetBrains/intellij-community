package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlin.streams.asSequence

interface ProjectModuleProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<ProjectModuleProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.projectModuleProvider")

        fun obtainAllProjectModulesFor(project: Project): Sequence<ProjectModule> =
            extensionPointName.extensions(project)
                .asSequence()
                .flatMap { it.obtainAllProjectModulesFor(project) }
                .distinctBy { it.buildFile }
    }

    fun obtainAllProjectModulesFor(project: Project): Sequence<ProjectModule>
}
