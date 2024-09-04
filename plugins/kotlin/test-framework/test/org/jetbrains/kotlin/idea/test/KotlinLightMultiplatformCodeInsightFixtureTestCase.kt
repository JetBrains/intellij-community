// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
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

    data class TestProjectFiles(
        val allFiles: List<VirtualFile>,
        val mainFile: VirtualFile?,
    )


    override fun setUp() {
        super.setUp()

        Registry.get("kotlin.k2.kmp.wasm.enabled").setValue(true, testRootDisposable)

        // sync is necessary to detect unexpected disappearances of library files
        VfsTestUtil.syncRefresh()
    }

    override fun tearDown() {
        runAll(
            { KotlinMultiPlatformProjectDescriptor.cleanupSourceRoots() },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinMultiPlatformProjectDescriptor
}
