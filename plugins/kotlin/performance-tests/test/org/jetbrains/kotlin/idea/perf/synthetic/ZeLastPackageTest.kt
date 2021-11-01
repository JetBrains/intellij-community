// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf.synthetic

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.perf.util.uploadAggregateResults
import org.jetbrains.kotlin.test.KotlinRoot
import java.io.File

/**
 * It has to be the last package test, see `com.intellij.TestCaseLoader#getClasses`
 */
class ZeLastPackageTest: TestCase() {
    fun test() {
        uploadAggregateResults(File(KotlinRoot.REPO, "out"))
    }
}