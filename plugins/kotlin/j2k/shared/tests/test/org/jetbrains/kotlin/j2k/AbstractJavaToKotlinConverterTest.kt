// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class AbstractJavaToKotlinConverterTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = J2K_PROJECT_DESCRIPTOR

    override fun setUp() {
        super.setUp()
        addJavaLangRecordClass()
    }

    protected fun addFile(fileName: String, dirName: String? = null) {
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

    protected fun addJpaColumnAnnotations() {
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

    protected fun addJunitTestAnnotations() {
        myFixture.addClass(
            """
            package org.junit;
            
            public @interface Test {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package org.junit.jupiter.api;
            
            public @interface Test {}
            """.trimIndent()
        )
    }
}