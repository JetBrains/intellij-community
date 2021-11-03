// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.whole

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.perf.util.uploadAggregateResults

/**
 * It has to be the last package test, see `com.intellij.TestCaseLoader#getClasses`
 */
class ZeLastPackageTest: TestCase() {
    fun test() {
        uploadAggregateResults()
    }
}