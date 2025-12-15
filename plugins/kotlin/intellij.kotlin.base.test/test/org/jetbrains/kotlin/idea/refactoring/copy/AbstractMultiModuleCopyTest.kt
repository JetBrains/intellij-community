// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.copy

import org.jetbrains.kotlin.idea.refactoring.loadTestConfiguration
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinMultiFileTestCase
import java.io.File

abstract class AbstractMultiModuleCopyTest : KotlinMultiFileTestCase() {

    override fun getTestRoot(): String = "/refactoring/copyMultiModule/"

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR

    fun doTest(path: String) {
        val config = loadTestConfiguration(File(path))

        isMultiModule = true

        doTestCommittingDocuments { rootDir, _ ->
            AbstractCopyTest.runCopyRefactoring(path, config, rootDir, project)
        }
    }
}