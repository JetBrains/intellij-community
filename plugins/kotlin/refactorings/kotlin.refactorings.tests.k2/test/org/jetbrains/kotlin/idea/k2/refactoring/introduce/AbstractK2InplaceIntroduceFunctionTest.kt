// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.KotlinFirExtractFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractInplaceIntroduceFunctionTest
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractK2InplaceIntroduceFunctionTest : AbstractInplaceIntroduceFunctionTest() {

    override fun getExtractFunctionHandler(allContainersEnabled: Boolean): AbstractExtractKotlinFunctionHandler =
        KotlinFirExtractFunctionHandler(allContainersEnabled)

    override fun tearDown() {
        runAll(
          { myFixture.project.invalidateCaches() },
          { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}