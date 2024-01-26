// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.ide.DataManager
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2IntroduceVariableTest : AbstractExtractionTest() {
    override fun isFirPlugin(): Boolean = true

    override fun doIntroduceVariableTest(unused: String) {
        doTestIfNotDisabledByFileDirective { file ->
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