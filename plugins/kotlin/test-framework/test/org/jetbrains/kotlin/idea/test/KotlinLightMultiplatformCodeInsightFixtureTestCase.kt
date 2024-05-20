// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import org.jetbrains.kotlin.idea.base.test.ModuleStructureSplitter
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.KotlinMultiPlatformProjectDescriptor.PlatformDescriptor
import org.jetbrains.kotlin.idea.test.util.slashedPath
import java.io.File

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

    /**
     * Configures the module structure based on the given file.
     *
     * File is expected to have the following structure:
     * // PLATFORM: <platform descriptor name> [org.jetbrains.kotlin.idea.test.KotlinMultiPlatformProjectDescriptor.PlatformDescriptor]
     * files
     *
     * // FILE: relativePath.kt (relative to the platform's source root)
     * // MAIN (if this file should be configured in the editor)
     * file content
     *
     * Each file is added to the test project's platform module.
     *
     * Returns a list of all files and a file which was marked as `MAIN` or `null` if it's absent.
     */
    fun configureModuleStructure(abstractFilePath: String): TestProjectFiles {
        val map = ModuleStructureSplitter.splitPerModule(File(abstractFilePath))
        var mainFile: VirtualFile? = null
        val allFiles: MutableList<VirtualFile> = mutableListOf()
        map.forEach { (platform, files) ->
            val platformDescriptor = PlatformDescriptor.entries.firstOrNull { it.moduleName.lowercase() == platform.lowercase() }
                ?: error("Unrecognized platform: $platform. Expected one of " +
                                 PlatformDescriptor.entries.joinToString(prefix = "[", postfix = "]") { it.moduleName })

            for (testFile in files) {
                val virtualFile = VfsTestUtil.createFile(platformDescriptor.sourceRoot()!!, testFile.relativePath, testFile.text)
                allFiles.add(virtualFile)
                if (testFile.isMain) {
                    mainFile = virtualFile
                }
                myFixture.configureFromExistingVirtualFile(virtualFile)
            }
        }
        return TestProjectFiles(allFiles, mainFile)
    }

    private var isKotlinK2KmpEnabledBeforeTest: Boolean? = null

    override fun setUp() {
        super.setUp()

        Registry.get("kotlin.k2.kmp.enabled").let { registryValue ->
            isKotlinK2KmpEnabledBeforeTest = registryValue.asBoolean()
            registryValue.setValue(true)
        }
    }

    override fun tearDown() {
        runAll(
            { KotlinMultiPlatformProjectDescriptor.cleanupSourceRoots() },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { Registry.get("kotlin.k2.kmp.enabled").setValue(isKotlinK2KmpEnabledBeforeTest == true) },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinMultiPlatformProjectDescriptor
}
