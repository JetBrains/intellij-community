// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.refactoring.InlineActionHandler
import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure
import java.io.File

abstract class AbstractKotlinFirMultiplatformTest: KotlinLightMultiplatformCodeInsightFixtureTestCase() {
    fun doTest(path: String) {
        val virtualFile = myFixture.configureMultiPlatformModuleStructure(path).mainFile
        require(virtualFile != null)

        myFixture.configureFromExistingVirtualFile(virtualFile)
        val targetElement = TargetElementUtil.findTargetElement(
            myFixture.editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        )
        val handler = if (targetElement != null)
            InlineActionHandler.EP_NAME.extensions.firstOrNull { it.canInlineElement(targetElement) }
        else
            null

        if (handler != null) {
            handler.inlineElement(project, editor, targetElement)
            myFixture.checkResultByFile("${File(path).nameWithoutExtension}.after.kt")
        } else {
            fail("No refactoring handler available")
        }
    }
}
