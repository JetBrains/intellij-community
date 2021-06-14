package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Extension point used to register [Module]s transformations to [ProjectModule]s.
 */
interface ModuleTransformer {

    companion object {

        internal val extensionPointName: ExtensionPointName<ModuleTransformer> =
            ExtensionPointName.create("com.intellij.packagesearch.moduleTransformer")

    }

    /**
     * Transforms [nativeModules] in a [ProjectModule] module if possible, else returns an empty list.
     * It's implementation should use the IntelliJ platform APIs for a given build system (eg.
     * Gradle or Maven), detect if and which [nativeModules] are controlled by said build system
     * and transform them accordingly.
     *
     * NOTE: some [Module]s in [nativeModules] may be already disposed or about to be. Be sure to
     * handle any exception and filter out the ones not working.
     *
     * @param nativeModules The native [Module]s that will be transformed.
     * @return [ProjectModule]s wrapping [nativeModules] or an empty list.
     */
    fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule>
}

/**
 * Transforms [nativeModules] in a [ProjectModule] module if possible, else returns an empty list.
 * It's implementation should use the IntelliJ platform APIs for a given build system (eg.
 * Gradle or Maven), detect if and which [nativeModules] are controlled by said build system
 * and transform them accordingly.
 *
 * NOTE: some [Module]s in [nativeModules] may be already disposed or about to be. Be sure to
 * handle any exception and filter out the ones not working.
 *
 * @param nativeModules The native [Module]s that will be transformed.
 * @return [ProjectModule]s wrapping [nativeModules] or an empty list.
 */
fun ModuleTransformer.transformModules(project: Project, nativeModules: Array<Module>): List<ProjectModule> =
    transformModules(project, nativeModules.toList())
