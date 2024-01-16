// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel.JDK_17
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService.Companion.DEBUG_LOG_ENABLE_PerModulePackageCache
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.allowProjectRootAccess
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.disposeVfsRootAccess
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractJavaToKotlinConverterTest : KotlinLightCodeInsightFixtureTestCase() {
    private var vfsDisposable: Ref<Disposable>? = null

    override fun setUp() {
        super.setUp()
        project.DEBUG_LOG_ENABLE_PerModulePackageCache = true

        val testName = getTestName(false)
        if (testName.contains("Java17") || testName.contains("java17")) {
            LanguageLevelProjectExtension.getInstance(project).languageLevel = JDK_17
        }
        vfsDisposable = allowProjectRootAccess(this)
        invalidateLibraryCache(project)
        addFile("KotlinApi.kt", "kotlinApi")
        addFile("JavaApi.java", "javaApi")
        addJavaLangRecordClass()
        addJpaColumnAnnotations()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { disposeVfsRootAccess(vfsDisposable) },
            ThrowableRunnable { project.DEBUG_LOG_ENABLE_PerModulePackageCache = false },
            ThrowableRunnable { super.tearDown() },
        )
    }

    private fun addFile(fileName: String, dirName: String? = null) {
        addFile(File(KotlinRoot.DIR, "j2k/k1.new/tests/testData/$fileName"), dirName)
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

    // Needed to make the Kotlin compiler think it is running on JDK 16+
    // see org.jetbrains.kotlin.resolve.jvm.checkers.JvmRecordApplicabilityChecker
    private fun addJavaLangRecordClass() {
        myFixture.addClass(
            """
            package java.lang;
            public abstract class Record {}
            """.trimIndent()
        )
    }

    private fun addJpaColumnAnnotations() {
        myFixture.addClass(
            """
            package javax.persistence;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.FIELD;
            import static java.lang.annotation.ElementType.METHOD;
            
            @Target({METHOD, FIELD})
            public @interface Column {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package jakarta.persistence;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.FIELD;
            import static java.lang.annotation.ElementType.METHOD;
            
            @Target({METHOD, FIELD})
            public @interface Column {}
            """.trimIndent()
        )
    }
}
