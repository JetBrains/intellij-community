// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.suite

import org.jetbrains.kotlin.idea.testFramework.Fixture
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.commitAllDocuments

abstract class AbstractFixtureMeasurementScope<T>(
    protected val fixture: Fixture,
    typeTestPrefix: String = "",
    name: String,
    stats: Stats,
    config: StatsScopeConfig,
    after: (() -> Unit)? = null,
    var revertChangesAtTheEnd: Boolean = true,
) : MeasurementScope<T>(listOf(typeTestPrefix, name).filter { it.isNotEmpty() }.joinToString(" "), stats, config, after = after) {
    protected fun <V> doFixturePerformanceTest(setUp: () -> Unit = {}, test:() -> V?, tearDown: () -> Unit = {}) {
        doPerformanceTest(
            setUp = {
                fixture.storeText()
                setUp()
                before.invoke()
            },
            test = test,
            tearDown = {
                try {
                    tearDown()
                } finally {
                    if (revertChangesAtTheEnd) {
                        fixture.restoreText()
                        commitAllDocuments()
                    }
                    after?.invoke()
                }
            }
        )
    }
}