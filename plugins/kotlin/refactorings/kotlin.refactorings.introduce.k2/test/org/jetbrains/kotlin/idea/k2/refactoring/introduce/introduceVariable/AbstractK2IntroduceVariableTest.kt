// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractExtractionTest

abstract class AbstractK2IntroduceVariableTest : AbstractExtractionTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getIntroduceVariableHandler(): RefactoringActionHandler = K2IntroduceVariableHandler

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}