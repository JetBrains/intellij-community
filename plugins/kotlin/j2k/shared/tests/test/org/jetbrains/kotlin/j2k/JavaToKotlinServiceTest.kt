// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.components.service
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class JavaToKotlinServiceTest : KotlinLightCodeInsightFixtureTestCase() {

    fun testConvertReportsTheFilesTheExternalUsagePassRewrote() {
        // A Kotlin caller of a Java getter is rewritten to property access when the getter becomes a property, so
        // the call edits a file the caller never named. Reporting only a boolean would hide it.
        myFixture.addFileToProject("Caller.kt", "fun use(m: Model) = m.getName()")
        val javaFile = myFixture.addFileToProject("Model.java", """
            public class Model {
                private String name = "x";
                public String getName() { return name; }
            }
        """.trimIndent()) as PsiJavaFile

        val result = runWithModalProgressBlocking(project, "") {
            project.service<JavaToKotlinService>().convert(listOf(javaFile), module)
        }

        assertEmpty(result.failed)
        val rewritten = result.externalUsageFiles.map { it.name }
        assertContainsElements(rewritten, "Caller.kt")
        assertFalse("Converted files must not be reported as external", rewritten.contains("Model.kt"))
    }

    fun testConvertReportsNoExternalFilesWhenNothingReferencesTheConvertedCode() {
        val javaFile = myFixture.addFileToProject("Lonely.java", "public class Lonely {}") as PsiJavaFile

        val result = runWithModalProgressBlocking(project, "") {
            project.service<JavaToKotlinService>().convert(listOf(javaFile), module)
        }

        assertEmpty(result.failed)
        assertEmpty(result.externalUsageFiles)
    }

}
