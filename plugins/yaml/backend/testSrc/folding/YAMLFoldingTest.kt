// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.folding

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.folding.YAMLFoldingSettings.Companion.getInstance

class YAMLFoldingTest : BasePlatformTestCase() {
  fun testFolding() {
    withDefaultFoldingSettings { defaultTest() }
  }

  fun testSequenceFolding() {
    withDefaultFoldingSettings { defaultTest() }
  }

  fun testRuby18677() {
    withDefaultFoldingSettings { defaultTest() }
  }

  fun testRuby22423() {
    withDefaultFoldingSettings { defaultTest() }
  }

  fun testComments() {
    withDefaultFoldingSettings { defaultTest() }
  }

  fun testRegionFolding() {
    withDefaultFoldingSettings { defaultTest() }
  }

  fun testAbbreviationLimit1() {
    withFoldingSettings(true, 1) { defaultTest() }
  }

  fun testAbbreviationLimit2() {
    withFoldingSettings(true, 2) { defaultTest() }
  }

  fun testAbbreviationLimit8() {
    withFoldingSettings(true, 8) { defaultTest() }
  }

  fun testNoAbbreviation() {
    withFoldingSettings(false, 8) { defaultTest() }
  }

  fun defaultTest() {
    myFixture.testFolding("$testDataPath${getTestName(true)}.yaml")
  }

  protected override fun getTestDataPath(): String {
    return "$TEST_DATA_PATH/folding/data/"
  }

  private fun withFoldingSettings(useAbbreviation: Boolean, abbreviationLengthLimit: Int, action: () -> Unit) {
    val settings = getInstance()
    val originalUseAbbreviation = settings.useAbbreviation
    val originalAbbreviationLengthLimit = settings.abbreviationLengthLimit
    try {
      settings.useAbbreviation = useAbbreviation
      settings.abbreviationLengthLimit = abbreviationLengthLimit
      action()
    }
    finally {
      settings.useAbbreviation = originalUseAbbreviation
      settings.abbreviationLengthLimit = originalAbbreviationLengthLimit
    }
  }

  private fun withDefaultFoldingSettings(action: () -> Unit) {
    withFoldingSettings(true, 20, action)
  }

  companion object {
    const val RELATIVE_TEST_DATA_PATH: String = "/plugins/yaml/backend/testData/org/jetbrains/yaml/"
    val TEST_DATA_PATH: String = PathManagerEx.getCommunityHomePath() + RELATIVE_TEST_DATA_PATH
  }
}