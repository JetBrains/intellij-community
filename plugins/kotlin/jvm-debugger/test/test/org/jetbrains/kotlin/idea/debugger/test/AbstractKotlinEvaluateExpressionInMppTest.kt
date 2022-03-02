// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.ExecutionTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.stubs.createMultiplatformFacetM3
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import java.io.File

/**
 * This class creates a project structure as follows:
 * |
 * | src/ <- jvm source root
 * |   ...
 * | common/ <- common source root
 * |   ...
 * The 'src' module has a compilation dependency on the 'common' module.
 */
abstract class AbstractKotlinEvaluateExpressionInMppTest : AbstractKotlinEvaluateExpressionTest() {
    override fun useIrBackend(): Boolean = true

    override fun setUpModule() {
        super.setUpModule()
        val jvmSrcPath = testAppPath + File.separator + ExecutionTestCase.SOURCES_DIRECTORY_NAME
        val commonSrcPath = testAppPath + File.separator + COMMON_MODULE_NAME
        val commonSrcDir = findVirtualFile(commonSrcPath) ?: error("Couldn't find common sources directory: $commonSrcPath")
        val commonModule = createModule(COMMON_MODULE_NAME)
        ExternalSystemApiUtil.doWriteAction {
            PsiTestUtil.addSourceRoot(commonModule, commonSrcDir)
            ModuleRootModificationUtil.addDependency(myModule, commonModule, DependencyScope.COMPILE, false)
            commonModule.createMultiplatformFacetM3(COMMON_MODULE_TARGET_PLATFORM, true, emptyList(), listOf(commonSrcPath))
            myModule.createMultiplatformFacetM3(JvmPlatforms.jvm8, true, listOf(COMMON_MODULE_NAME), listOf(jvmSrcPath))
        }
    }

    companion object {
        private val COMMON_MODULE_TARGET_PLATFORM =
            TargetPlatform(
                setOf(
                    JvmPlatforms.jvm8.single(),
                    JsPlatforms.defaultJsPlatform.single(),
                    NativePlatforms.unspecifiedNativePlatform.single()
                )
            )
    }
}

private fun findVirtualFile(path: String): VirtualFile? =
    LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'))

