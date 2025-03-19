// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.test.kotlin

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.UastReferenceTestBase
import org.junit.Test
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinUastReferencesTest : UastReferenceTestBase, KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    @Test
    fun `test original getter is visible when reference is under renaming`() {
        `check original getter is visible when reference is under renaming`(myFixture)
    }

    @Test
    fun testConstructorCallPattern() {
        checkConstructorCallPattern(myFixture)
    }
}
