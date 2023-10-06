// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.ide.DataManager
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractK2IntroduceVariableTest : AbstractExtractionTest() {
    override fun isFirPlugin(): Boolean = true

    override fun doIntroduceVariableTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFilePath(),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            doTest { file ->
                file as KtFile

                KotlinIntroduceVariableHandler.invoke(
                    fixture.project,
                    fixture.editor,
                    file,
                    DataManager.getInstance().getDataContext(fixture.editor.component)
                )
            }
        }
    }
}