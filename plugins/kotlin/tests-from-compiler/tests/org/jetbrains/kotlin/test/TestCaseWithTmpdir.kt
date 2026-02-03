// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused", "MemberVisibilityCanBePrivate") // used at runtime

package org.jetbrains.kotlin.test

import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import java.io.File

abstract class TestCaseWithTmpdir : KtUsefulTestCase() {
    protected lateinit var tmpdir: File

    override fun setUp() {
        super.setUp()
        tmpdir = KotlinTestUtils.tmpDirForTest(this)
    }
}