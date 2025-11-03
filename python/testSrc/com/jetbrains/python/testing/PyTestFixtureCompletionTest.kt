// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.jetbrains.python.fixtures.PyTestCase

class PyTestFixtureCompletionTest : PyTestCase() {
    override fun getTestDataPath(): String = super.getTestDataPath() + "/completion/pytestFixture"

    fun testRequestFixtureCompletion() {
        myFixture.configureByFile("test_request_fixture_completion.py")
        myFixture.completeBasic()
        myFixture.checkResultByFile("test_request_fixture_completion.after.py")
    }
}
