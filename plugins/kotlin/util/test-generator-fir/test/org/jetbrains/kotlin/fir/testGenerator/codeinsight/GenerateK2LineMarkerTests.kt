// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.idea.k2.codeInsight.lineMarkers.test.AbstractKotlinPsiBasedTestFrameworkK2Test
import org.jetbrains.kotlin.idea.k2.codeInsight.lineMarkers.test.AbstractLineMarkersK2Test
import org.jetbrains.kotlin.testGenerator.model.*
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.*

internal fun MutableTWorkspace.generateK2LineMarkerTests() {
    testGroup("code-insight/line-markers", category = HIGHLIGHTING) {
        testClass<AbstractLineMarkersK2Test> {
            model("recursive", pattern = Patterns.KT_WITHOUT_DOTS)
            model("suspend", pattern = Patterns.KT_WITHOUT_DOTS)
            model("../../../idea/tests/testData/codeInsight/lineMarker/overrideImplement", pattern = Patterns.KT_WITHOUT_DOTS)
            model("../../../idea/tests/testData/codeInsight/lineMarker/dslMarker", pattern = Patterns.KT_WITHOUT_DOTS)
            model("../../../idea/tests/testData/codeInsight/lineMarker/main", pattern = Patterns.KT_WITHOUT_DOTS)
            model("../../../idea/tests/testData/codeInsight/lineMarker/runMarkers", pattern = Patterns.KT_WITHOUT_DOTS)
            model("../../../idea/tests/testData/codeInsight/lineMarker/methodSeparators", pattern = Patterns.KT_WITHOUT_DOTS)

        }

        testClass<AbstractKotlinPsiBasedTestFrameworkK2Test> {
            model(
                "../../../idea/tests/testData/codeInsight/lineMarker/runMarkers",
                pattern = Patterns.forRegex("^((jUnit|test)\\w*)\\.kt$"),
                testMethodName = "doPsiBasedTest",
                testClassName = "WithLightTestFramework"
            )
            model(
                "../../../idea/tests/testData/codeInsight/lineMarker/runMarkers",
                pattern = Patterns.forRegex("^((jUnit|test)\\w*)\\.kt$"),
                testMethodName = "doPureTest",
                testClassName = "WithoutLightTestFramework"
            )
            model(
                "../../../idea/tests/testData/codeInsight/lineMarker/runMarkers",
                pattern = Patterns.forRegex("^((jUnit|test)\\w*)\\.kt$"),
                testMethodName = "doTestWithGradleConfiguration",
                testClassName = "WithGradleConfiguration"
            )
        }
    }
}