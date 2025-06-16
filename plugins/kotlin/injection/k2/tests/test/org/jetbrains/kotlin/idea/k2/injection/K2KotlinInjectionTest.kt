// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.injection.KotlinInjectionTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class K2KotlinInjectionTest: KotlinInjectionTestBase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    fun testKotlinReplaceWithBuildInsInjection() {
        myFixture.configureByText(
            "a.kt",
            """
            @kotlin.Deprecated("it's outdated", kotlin.ReplaceWith("do<caret>Fun(42)"))
            fun bar() {}
            """.trimIndent()
        )

        assertFalse(
            "Injection action is available. There's probably no injection at caret place",
            isInjectLanguageActionAvailable()
        )

        assertEquals("Wrong injection language", KotlinLanguage.INSTANCE, injectedFile?.language)
    }
}
