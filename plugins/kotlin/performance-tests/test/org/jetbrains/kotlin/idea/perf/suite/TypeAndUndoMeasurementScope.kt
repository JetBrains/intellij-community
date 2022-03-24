// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.suite

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.testFramework.Fixture
import org.jetbrains.kotlin.idea.testFramework.Stats

class TypeAndUndoMeasurementScope(
    fixture: Fixture,
    typeTestPrefix: String = "",
    name: String,
    stats: Stats,
    config: StatsScopeConfig,
    after: (() -> Unit)? = null,
    revertChangesAtTheEnd: Boolean = true,
) : AbstractFixtureMeasurementScope<String>(fixture, typeTestPrefix, name, stats, config, after, revertChangesAtTheEnd) {
    override fun run(): List<String?> {
        doFixturePerformanceTest(
            setUp = {
                fixture.typingConfig.moveCursor()
                fixture.type()
            },
            test = {
                fixture.performEditorAction(IdeActions.ACTION_UNDO)
                UIUtil.dispatchAllInvocationEvents()
            },
            tearDown = {
                val text = fixture.document.text
                val savedText = fixture.savedText
                assert(savedText != text) { "undo has to change document text\nbefore undo:\n$savedText\n\nafter undo:\n$text" }
            }
        )
        return listOf(fixture.document.text)
    }
}