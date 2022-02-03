// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService.Companion.DEBUG_LOG_ENABLE_PerModulePackageCache
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.*
import java.io.File

abstract class AbstractJavaToKotlinConverterTest : KotlinLightCodeInsightFixtureTestCase() {
    private var vfsDisposable: Ref<Disposable>? = null

    override fun setUp() {
        super.setUp()

        project.DEBUG_LOG_ENABLE_PerModulePackageCache = true

        val testName = getTestName(false)
        if (testName.contains("Java8") || testName.contains("java8")) {
            LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_8
        }

        vfsDisposable = allowProjectRootAccess(this)

        invalidateLibraryCache(project)

        addFile("KotlinApi.kt", "kotlinApi")
        addFile("JavaApi.java", "javaApi")
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { disposeVfsRootAccess(vfsDisposable) },
            ThrowableRunnable { project.DEBUG_LOG_ENABLE_PerModulePackageCache = false },
            ThrowableRunnable { super.tearDown() },
        )
    }

    protected fun addFile(fileName: String, dirName: String? = null) {
        addFile(File(KotlinRoot.DIR, "j2k/old/tests/testData/$fileName"), dirName)
    }

    protected fun addFile(file: File, dirName: String?): VirtualFile {
        return addFile(FileUtil.loadFile(file, true), file.name, dirName)
    }

    protected fun addFile(text: String, fileName: String, dirName: String?): VirtualFile {
        val filePath = (if (dirName != null) "$dirName/" else "") + fileName
        return myFixture.addFileToProject(filePath, text).virtualFile
    }

    protected fun deleteFile(virtualFile: VirtualFile) {
        runWriteAction { virtualFile.delete(this) }
    }
}

