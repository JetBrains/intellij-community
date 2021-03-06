// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.mlcompletion.prev2calls.PrevCallsModelsProviderService

class PyPrevCallsFeatureTest: PyMlCompletionTestCase() {
  override fun getTestDataPath(): String = super.getTestDataPath() + "/codeInsight/mlcompletion/prev2calls"

  fun testHaveOsModel() {
    PrevCallsModelsProviderService.instance.haveModelForQualifier("os")
    PrevCallsModelsProviderService.instance.haveModelForQualifier("os.path")
  }

  fun testOsPathPrimaryWeight() = checkFeaturesPresence("os", "path",
                                                        listOf("prev_2_calls_primary_weight"),
                                                        listOf("prev_2_calls_weight_empty_prev_calls",
                                                               "prev_2_calls_weight_secondary_two_calls",
                                                               "prev_2_calls_weight_one_call"))

  fun testOsPathEmptyPrevCallsWeight() = checkFeaturesPresence("os", "path",
                          listOf("prev_2_calls_weight_empty_prev_calls"),
                          listOf("prev_2_calls_primary_weight",
                                 "prev_2_calls_weight_secondary_two_calls",
                                 "prev_2_calls_weight_one_call"))

  fun testOsMakedirsPathPrimaryWeight() = checkFeaturesPresence("os", "path",
                                                                listOf("prev_2_calls_primary_weight"),
                                                                listOf("prev_2_calls_weight_one_call",
                                                                      "prev_2_calls_weight_secondary_two_calls",
                                                                      "prev_2_calls_weight_empty_prev_calls"))

  fun testOsMakedirsPathOneCallWeight() = checkFeaturesPresence("os", "path",
                                                                listOf("prev_2_calls_weight_one_call"),
                                                                listOf("prev_2_calls_primary_weight",
                                                                       "prev_2_calls_weight_secondary_two_calls",
                                                                       "prev_2_calls_weight_empty_prev_calls"))

  fun testOsEnvironGet() = checkFeaturesPresence("os.environ", "get",
                                                 listOf("prev_2_calls_primary_weight"),
                                                 listOf("prev_2_calls_weight_one_call",
                                                                       "prev_2_calls_weight_secondary_two_calls",
                                                                       "prev_2_calls_weight_empty_prev_calls"))

  fun testSysStdinReadlineRstrip() = checkFeaturesPresence("sys.stdin.readline", "rstrip",
                                                 listOf("prev_2_calls_primary_weight"),
                                                 listOf("prev_2_calls_weight_one_call",
                                                        "prev_2_calls_weight_secondary_two_calls",
                                                        "prev_2_calls_weight_empty_prev_calls"))

  fun testSysStdinReadlinesIter() = checkFeaturesPresence("sys.stdin", "readlines",
                                                           listOf("prev_2_calls_primary_weight"),
                                                           listOf("prev_2_calls_weight_one_call",
                                                                  "prev_2_calls_weight_secondary_two_calls",
                                                                  "prev_2_calls_weight_empty_prev_calls"))

  fun testOsEnvironGetStartsWithCond() = checkFeaturesPresence("os.environ.get", "startswith",
                                                               listOf("prev_2_calls_primary_weight"),
                                                               listOf("prev_2_calls_weight_one_call",
                                                                      "prev_2_calls_weight_secondary_two_calls",
                                                                      "prev_2_calls_weight_empty_prev_calls"))

  private fun checkFeaturesPresence(moduleName: String,
                                    elementName: String,
                                    expectedFeatures: List<String>,
                                    notExpectedFeatures: List<String>) {
    assertTrue(PrevCallsModelsProviderService.instance.haveModelForQualifier(moduleName))

    doWithInstalledProviders(PyContextFeatureProvider(), PyElementFeatureProvider(), PythonLanguage.INSTANCE) { _, elementFeaturesProvider ->
      invokeCompletion()

      val selected = myFixture.lookupElements!!.find { it.lookupString == elementName }
      assertNotNull(selected)

      val features = elementFeaturesProvider.features[selected]
      assertNotNull(features)

      for (feature in expectedFeatures) {
        assertTrue(features!!.containsKey(feature))
      }

      for (feature in notExpectedFeatures) {
        assertFalse(features!!.containsKey(feature))
      }
    }
  }
}