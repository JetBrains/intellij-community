// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.injection.KotlinTrimmedInjectionTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.util.invalidateCaches

class K2KotlinTrimmedInjectionTest: KotlinTrimmedInjectionTestBase() {

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
}