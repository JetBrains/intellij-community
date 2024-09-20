// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.test.KotlinMultiPlatformProjectDescriptor.PlatformDescriptor
import java.io.File

/**
 * Configures the module structure based on the given file.
 *
 * The file is expected to have the following structure:
 * ```
 * // PLATFORM: <platform descriptor name>
 * // FILE: relativePath.kt (relative to the platform's source root, multiple files can be declared per PLATFORM, `.java` files in JVM are allowed).
 * // MAIN (if this file should be configured in the editor)
 * file content
 * ```
 * Platform descriptor names come from  [PlatformDescriptor].
 * Each file is added to the platform module declared before it.
 *
 * @return a list of all files and a file which was marked as `MAIN` (or `null` if it's absent).
 */
fun JavaCodeInsightTestFixture.configureMultiPlatformModuleStructure(abstractFilePath: String): MultiplatformTestProjectFiles {
    val map = ModuleStructureSplitter.splitPerModule(File(abstractFilePath))
    var mainFile: VirtualFile? = null
    val allFiles: MutableList<VirtualFile> = mutableListOf()
    map.forEach { (platform, files) ->
        val platformDescriptor = PlatformDescriptor.entries.firstOrNull { it.moduleName.lowercase() == platform.lowercase() }
                                 ?: error("Unrecognized platform: $platform. Expected one of " +
                                          PlatformDescriptor.entries.joinToString(prefix = "[",
                                                                                  postfix = "]") { it.moduleName })

        for (testFile in files) {
            val virtualFile = VfsTestUtil.createFile(
              platformDescriptor.selectSourceRootByFilePath(testFile.relativePath)!!, testFile.relativePath, testFile.text,
            )
            allFiles.add(virtualFile)
            if (testFile.isMain) {
                mainFile = virtualFile
            }
            configureFromExistingVirtualFile(virtualFile)
        }
    }
    return MultiplatformTestProjectFiles(allFiles, mainFile)
}

data class MultiplatformTestProjectFiles(
    val allFiles: List<VirtualFile>,
    val mainFile: VirtualFile?,
)
