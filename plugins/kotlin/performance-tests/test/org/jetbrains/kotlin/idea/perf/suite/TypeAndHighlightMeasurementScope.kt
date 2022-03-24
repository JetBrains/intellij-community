// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.suite

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import org.jetbrains.kotlin.idea.testFramework.Fixture
import org.jetbrains.kotlin.idea.testFramework.Stats

class TypeAndHighlightMeasurementScope(
    fixture: Fixture,
    typeTestPrefix: String = "",
    name: String,
    stats: Stats,
    config: StatsScopeConfig,
    after: (() -> Unit)? = null,
    revertChangesAtTheEnd: Boolean = true,
) : AbstractFixtureMeasurementScope<HighlightInfo>(fixture, typeTestPrefix, name, stats, config, after, revertChangesAtTheEnd) {
    override fun run(): List<HighlightInfo?> {
        var highlightInfos: List<HighlightInfo>? = null
        doFixturePerformanceTest(
            setUp = {
                fixture.typingConfig.moveCursor()
            },
            test = {
                highlightInfos = fixture.typeAndHighlight()
                highlightInfos
            },
            tearDown = {
                highlightInfos?.let { assertNotEmpty(it) }
            }
        )
        return highlightInfos ?: emptyList()
    }
}