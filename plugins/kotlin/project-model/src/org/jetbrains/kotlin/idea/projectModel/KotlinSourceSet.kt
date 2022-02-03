// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.File

interface KotlinSourceSet : KotlinComponent {
    val languageSettings: KotlinLanguageSettings
    val sourceDirs: Set<File>
    val resourceDirs: Set<File>
    val actualPlatforms: KotlinPlatformContainer

    /**
     * Special dependencies, that shall not be passed transitively to any depending source sets
     */
    val intransitiveDependencies: Array<KotlinDependencyId>

    /**
     * Dependencies that can be forworded transitively to any dependending source set
     */
    val regularDependencies: Array<KotlinDependencyId>

    /**
     * All dependencies ( [regularDependencies] + [intransitiveDependencies])
     */
    override val dependencies: Array<KotlinDependencyId>


    /**
     * All source sets that this source set explicitly declared a 'dependsOn' relation to
     */
    val declaredDependsOnSourceSets: Set<String>

    /**
     * The whole transitive closure of source sets this source set depends on.
     * ([declaredDependsOnSourceSets] and their dependencies recursively)
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        "This property might be misleading. " +
                "Replace with 'KotlinSourceSetContainer.resolveAllDependsOnSourceSets' to make intention of " +
                "receiving the full transitive closure explicit",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("allDependsOnSourceSets")
    )
    val dependsOnSourceSets: Set<String>
        get() = allDependsOnSourceSets

    /**
     * The whole transitive closure of source sets this source set depends on.
     * ([declaredDependsOnSourceSets] and their dependencies recursively)
     */
    @Deprecated(
        "This set of source sets might be inconsistent with any KotlinSourceSetContainer different to the one used to build this instance" +
                "Replace with 'KotlinSourceSetContainer.resolveAllDependsOnSourceSets' to get consistent resolution",
        level = DeprecationLevel.WARNING
    )
    val allDependsOnSourceSets: Set<String>

    val additionalVisibleSourceSets: Set<String>

    @Deprecated(
        "Returns single target platform. Use actualPlatforms instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("actualPlatforms.platforms.singleOrNull() ?: KotlinPlatform.COMMON")
    )
    val platform: KotlinPlatform
        get() = actualPlatforms.platforms.singleOrNull() ?: KotlinPlatform.COMMON


    companion object {
        const val COMMON_MAIN_SOURCE_SET_NAME = "commonMain"
        const val COMMON_TEST_SOURCE_SET_NAME = "commonTest"

        // Note. This method could not be deleted due to usage in KotlinAndroidGradleMPPModuleDataService from IDEA Core
        @Suppress("unused")
        fun commonName(forTests: Boolean) = if (forTests) COMMON_TEST_SOURCE_SET_NAME else COMMON_MAIN_SOURCE_SET_NAME
    }
}
