// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.util

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

fun KotlinLightCodeInsightFixtureTestCase.removeInfoMarkers() {
    ExpectedHighlightingData(editor.document, true, true).init()

    runInEdtAndWait {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
