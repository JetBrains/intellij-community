// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk

abstract class AbstractJavaToKotlinConverterSingleFileFullJDKTest : AbstractJavaToKotlinConverterSingleFileTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor = getInstanceFullJdk()
}