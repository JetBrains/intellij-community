// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.util.slashedPath
import java.io.File

/**
 * See [the YT KB article](https://youtrack.jetbrains.com/articles/KTIJ-A-50/Light-Multiplatform-Tests)
 */
abstract class KotlinLightMultiplatformCodeInsightFixtureTestCase : KotlinLightCodeInsightFixtureTestCaseBase() {

    @Deprecated("Migrate to 'testDataDirectory'.", ReplaceWith("testDataDirectory"))
    final override fun getTestDataPath(): String = testDataDirectory.slashedPath

    open val testDataDirectory: File by lazy {
        File(TestMetadataUtil.getTestDataPath(javaClass))
    }


    override fun setUp() {
        super.setUp()

        // sync is necessary to detect unexpected disappearances of library files
        VfsTestUtil.syncRefresh()
    }

    override fun tearDown() {
        runAll(
            { projectDescriptor.cleanupSourceRoots() },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): KotlinMultiPlatformProjectDescriptor = KotlinMultiPlatformProjectDescriptor.ALL_PLATFORMS
}
