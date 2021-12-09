// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.kotlin.incremental.testingUtils.BuildLogFinder
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget

/**
 * @see [jps-plugin/tests/testData/incremental/cacheVersionChanged/README.md]
 */
abstract class AbstractDataContainerVersionChangedTest30 : AbstractIncrementalCacheVersionChangedTest35() {
    override val buildLogFinder: BuildLogFinder
        get() = BuildLogFinder(isDataContainerBuildLogEnabled = true)

    override fun getVersionManagersToTest(target: KotlinModuleBuildTarget<*>) =
        listOf(kotlinCompileContext.lookupsCacheAttributesManager.versionManagerForTesting)
}