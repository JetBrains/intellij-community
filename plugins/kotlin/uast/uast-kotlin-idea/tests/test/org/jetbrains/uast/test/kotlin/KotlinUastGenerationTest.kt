// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.uast.test.kotlin

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction

class KotlinUastGenerationTest : AbstractKotlinUastGenerationTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()


    override fun `test lambda expression`() {
        // ignore: error type was treated as NotNull, now no nullubility is provided
    }

    override fun `test lambda expression with simplified block body with context`() {
        // ignore: error type was treated as NotNull, now no nullubility is provided
    }

    override fun `test suggested name`() {
        // ignore: the suggested name is changed for vars
    }
}
