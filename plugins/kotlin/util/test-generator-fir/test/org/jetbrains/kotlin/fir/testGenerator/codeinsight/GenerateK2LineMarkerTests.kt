// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.test.AbstractSuspendLineMarkerTest
import org.jetbrains.kotlin.testGenerator.model.*

internal fun MutableTWorkspace.generateK2LineMarkerTests() {
    testGroup("code-insight/line-markers") {
        testClass<AbstractSuspendLineMarkerTest> {
            model("suspend", pattern = Patterns.KT_WITHOUT_DOTS, passTestDataPath = false)
        }
    }
}