// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.util

import junit.framework.TestCase

/**
 * To be executed in the end of all tests, subclasses have to be the last package test,
 * see `com.intellij.TestCaseLoader#getClasses`
 *
 * As there tests are running as performance tests (see `com.intellij.TestCaseLoader#PERFORMANCE_TESTS_ONLY_FLAG`),
 * subclasses have to be named with `Performance` in their name
 *
 */
abstract class AbstractZeLastPackagePerformanceTest: TestCase() {
    fun test() {
        uploadAggregateResults()
    }
}