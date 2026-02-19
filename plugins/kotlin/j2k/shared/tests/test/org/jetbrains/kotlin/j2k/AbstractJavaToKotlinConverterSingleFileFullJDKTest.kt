// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.testFramework.LightProjectDescriptor

abstract class AbstractJavaToKotlinConverterSingleFileFullJDKTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor = J2K_FULL_JDK_PROJECT_DESCRIPTOR
}