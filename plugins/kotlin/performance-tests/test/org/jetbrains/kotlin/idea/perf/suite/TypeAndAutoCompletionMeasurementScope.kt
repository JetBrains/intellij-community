// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.suite

import com.intellij.codeInsight.lookup.LookupElement
import junit.framework.TestCase.assertTrue
import org.jetbrains.kotlin.idea.testFramework.Fixture
import org.jetbrains.kotlin.idea.testFramework.Stats

class TypeAndAutoCompletionMeasurementScope(
    fixture: Fixture,
    typeTestPrefix: String = "",
    name: String,
    stats: Stats,
    config: StatsScopeConfig,
    after: (() -> Unit)? = null,
    var lookupElements: List<String> = listOf(),
    revertChangesAtTheEnd: Boolean = true,
): AbstractFixtureMeasurementScope<String>(fixture, typeTestPrefix, name, stats, config, after, revertChangesAtTheEnd) {

    var lookupElement: String
        get() = lookupElements.single()
        set(value) {
            lookupElements = listOf(value)
        }

    override fun run(): List<String?> {
        assertTrue("lookupElements has to be not empty", lookupElements.isNotEmpty())
        var value: Array<LookupElement>? = null
        doFixturePerformanceTest(
            setUp = {
                fixture.typingConfig.moveCursor()
                fixture.type()
            },
            test = { value = fixture.complete() },
            tearDown = {
                val items = value?.map { e -> e.lookupString }?.toList() ?: emptyList()
                for (lookupElement in lookupElements) {
                    assertTrue("'$lookupElement' has to be present in items $items", items.contains(lookupElement))
                }
            }
        )
        return value?.map { e -> e.lookupString }?.toList() ?: emptyList()
    }

}
