// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.test.IgnoreTests.DIRECTIVES.IGNORE_K1
import org.jetbrains.kotlin.idea.base.test.IgnoreTests.DIRECTIVES.IGNORE_K2
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService.Companion.DEBUG_LOG_ENABLE_PerModulePackageCache
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

private val ignoreDirectives: Set<String> = setOf(IGNORE_K1, IGNORE_K2)

abstract class AbstractJavaToKotlinConverterTest : KotlinLightCodeInsightFixtureTestCase() {
    private var vfsDisposable: Ref<Disposable>? = null

    override fun setUp() {
        super.setUp()
        project.DEBUG_LOG_ENABLE_PerModulePackageCache = true

        val testName = getTestName(false)
        if (testName.contains("Java17") || testName.contains("java17")) {
            IdeaTestUtil.setProjectLanguageLevel(project, LanguageLevel.JDK_17)
        }
        vfsDisposable = KotlinTestUtils.allowProjectRootAccess(this)
        invalidateLibraryCache(project)
        addFile("KotlinApi.kt", "kotlinApi")
        addFile("JavaApi.java", "javaApi")
        addJavaLangRecordClass()
        addJpaColumnAnnotations()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { KotlinTestUtils.disposeVfsRootAccess(vfsDisposable) },
            ThrowableRunnable { project.DEBUG_LOG_ENABLE_PerModulePackageCache = false },
            ThrowableRunnable { super.tearDown() },
        )
    }

    private fun addFile(fileName: String, dirName: String? = null) {
        addFile(File(KotlinRoot.DIR, "j2k/shared/tests/testData/$fileName"), dirName)
    }

    protected fun addFile(file: File, dirName: String? = null): VirtualFile {
        return addFile(FileUtil.loadFile(file, true), file.name, dirName)
    }

    protected fun addFile(text: String, fileName: String, dirName: String?): VirtualFile {
        val filePath = (if (dirName != null) "$dirName/" else "") + fileName
        return myFixture.addFileToProject(filePath, text).virtualFile
    }

    protected fun deleteFile(virtualFile: VirtualFile) {
        runWriteAction { virtualFile.delete(this) }
    }

    protected fun getDisableTestDirective(): String =
        if (isFirPlugin) IGNORE_K2 else IGNORE_K1

    protected fun File.getFileTextWithoutDirectives(): String =
        readText().getTextWithoutDirectives()

    protected fun String.getTextWithoutDirectives(): String =
        split("\n").filterNot { it.trim() in ignoreDirectives }.joinToString(separator = "\n")

    protected fun KtFile.getFileTextWithErrors(): String =
        if (isFirPlugin) getK2FileTextWithErrors(this) else dumpTextWithErrors()

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
