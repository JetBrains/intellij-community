// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.kotlin.incremental.testingUtils.Modification
import org.jetbrains.kotlin.incremental.testingUtils.ModifyContent
import org.jetbrains.kotlin.jps.incremental.CacheVersionManager
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget

/**
 * @see [jps-plugin/tests/testData/incremental/cacheVersionChanged/README.md]
 */
abstract class AbstractIncrementalCacheVersionChangedTest35 : AbstractIncrementalJvmJpsTest(allowNoFilesWithSuffixInTestData = true) {
    override fun performAdditionalModifications(modifications: List<Modification>) {
        val modifiedFiles = modifications.filterIsInstance<ModifyContent>().map { it.path }
        val targets = projectDescriptor.allModuleTargets
        val hasKotlin = HasKotlinMarker(projectDescriptor.dataManager)

        if (modifiedFiles.any { it.endsWith("clear-has-kotlin") }) {
            targets.forEach { hasKotlin.clean(it) }
        }

        if (modifiedFiles.none { it.endsWith("do-not-change-cache-versions") }) {
            val versions = targets.flatMap {
                getVersionManagersToTest(kotlinCompileContext.targetsBinding[it]!!)
            }

            versions.forEach {
                if (it.versionFileForTesting.exists()) {
                    it.versionFileForTesting.writeText("777")
                }
            }
        }
    }

    protected open fun getVersionManagersToTest(target: KotlinModuleBuildTarget<*>): List<CacheVersionManager> =
        listOf(target.localCacheVersionManager)
}
