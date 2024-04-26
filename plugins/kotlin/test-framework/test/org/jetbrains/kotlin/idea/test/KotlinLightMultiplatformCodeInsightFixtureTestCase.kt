// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import org.jetbrains.kotlin.idea.base.test.ModuleStructureSplitter
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.util.slashedPath
import java.io.File

abstract class KotlinLightMultiplatformCodeInsightFixtureTestCase : KotlinLightCodeInsightFixtureTestCaseBase() {

    @Deprecated("Migrate to 'testDataDirectory'.", ReplaceWith("testDataDirectory"))
    final override fun getTestDataPath(): String = testDataDirectory.slashedPath

    open val testDataDirectory: File by lazy {
        File(TestMetadataUtil.getTestDataPath(javaClass))
    }

    /**
     * Configures the module structure based on the given file.
     *
     * File is expected to have the following structure:
     * // PLATFORM: (Common|Jvm|Js) [org.jetbrains.kotlin.idea.test.KotlinMultiPlatformProjectDescriptor.PlatformDescriptor]
     * files
     *
     * // FILE: relativePath.kt (relative to the platform's source root)
     * // MAIN (if this file should be configured in the editor)
     * file content
     *
     * Each file is added to the test project's platform module.
     *
     * Returns file which was marked as `MAIN` or null
     */
    fun configureModuleStructure(abstractFilePath: String): VirtualFile? {
        val map = ModuleStructureSplitter.splitPerModule(File(abstractFilePath))
        var mainFile: VirtualFile? = null
        map.forEach { (platform, files) ->
            val platformDescriptor = when (platform) {
                "Common" -> KotlinMultiPlatformProjectDescriptor.PlatformDescriptor.COMMON
                "Jvm" -> KotlinMultiPlatformProjectDescriptor.PlatformDescriptor.JVM
                "Js" -> KotlinMultiPlatformProjectDescriptor.PlatformDescriptor.JS
                else -> null
            }
            if (platformDescriptor != null) {
                for (testFile in files) {
                    val virtualFile = VfsTestUtil.createFile(platformDescriptor.sourceRoot()!!, testFile.relativePath, testFile.text)
                    if (testFile.isMain) {
                        mainFile = virtualFile
                    }
                    myFixture.configureFromExistingVirtualFile(virtualFile)
                }
            }
        }
        return mainFile
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
