package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlin.streams.asSequence

/**
 * Extension point that allows to look up all the [ProjectModule]s in a [Project].
 */
interface ProjectModuleProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<ProjectModuleProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.projectModuleProvider")

        /**
         * Looks up all the [ProjectModule] in a given [project] for each registered
         * implementation of [ProjectModuleProvider].
         * @return A [Sequence]<[ProjectModule]> found in [project].
         */
        fun obtainAllProjectModulesFor(project: Project): Sequence<ProjectModule> =
            extensionPointName.extensions(project)
                .asSequence()
                .flatMap { it.obtainAllProjectModulesFor(project) }
    }

    /**
     * Looks up all the [ProjectModule] in a given [project].
     * @return A [Sequence]<[ProjectModule]> found in [project].
     */
    fun obtainAllProjectModulesFor(project: Project): Sequence<ProjectModule>
}
