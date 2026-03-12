// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.jvm.k2.scratch.AbstractK2ScratchRunActionTest
import org.jetbrains.kotlin.testGenerator.model.GroupCategory
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2ScratchTests() {
    testGroup("jvm/k2", category = GroupCategory.SCRIPTS) {
        testClass<AbstractK2ScratchRunActionTest> {
            model("scratch", pattern = Patterns.KTS, testMethodName = "doScratchTest", isRecursive = false)
        }
    }
}
