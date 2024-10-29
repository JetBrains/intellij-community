package com.intellij.searchEverywhereMl.semantics.tests

import kotlinx.coroutines.test.runTest
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class MockTest: LightJavaCodeInsightFixtureTestCase() {
    fun `test stub`() = runTest {
      assertEquals(1.0.toInt(), 1)
    }
}