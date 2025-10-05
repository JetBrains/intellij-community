// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.findUsages

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import de.plushnikov.intellij.plugin.LombokTestUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinLombokFindUsagesTest: KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR
    }

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testFindUsagesForSetter() {
        val aClass = myFixture.addClass(
            """import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SomeJavaClass {

    private int someValue;
}"""
        )
        myFixture.addFileToProject("usage.kt", """
            fun main(val a: SomeJavaClass) {
                a.someValue = 10
            }
        """.trimIndent())
        val field = aClass.fields[0]

        val usages = (myFixture as CodeInsightTestFixtureImpl).findUsages(field)

        assertNotEmpty(usages)
    }
}