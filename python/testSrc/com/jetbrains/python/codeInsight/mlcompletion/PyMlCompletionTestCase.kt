// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.fixtures.PyTestCase
import org.junit.Assert

open class PyMlCompletionTestCase: PyTestCase() {
  fun doContextFeaturesTest(vararg expected: Pair<String, MLFeatureValue>) = doContextFeaturesTest(listOf(*expected), emptyList())

  fun doContextFeaturesTest(expectedDefined: List<Pair<String, MLFeatureValue>>, expectedUndefined: List<String>) {
    doWithInstalledProviders { contextFeaturesProvider, _ ->
      invokeCompletion()
      assertHasFeatures(contextFeaturesProvider.features, expectedDefined)
      assertHasNotFeatures(contextFeaturesProvider.features, expectedUndefined)
    }
  }

  fun doElementFeaturesTest(checks: List<Pair<String, List<Pair<String, MLFeatureValue>>>>) {
    checks.forEach {
      doElementFeaturesTest(it.first, it.second, emptyList())
    }
  }

  fun doElementFeaturesTest(elementToSelect: String, vararg expected: Pair<String, MLFeatureValue>) {
    doElementFeaturesTest(elementToSelect, arrayListOf(*expected), emptyList())
  }

  fun doElementFeaturesTest(elementToSelect: String,
                                    expectedDefined: List<Pair<String, MLFeatureValue>>,
                                    expectedUndefined: List<String>) {
    val selector: (LookupElement) -> Boolean = { it.lookupString == elementToSelect }
    doElementFeaturesInternalTest(selector, expectedDefined, expectedUndefined)
  }

  private fun doElementFeaturesInternalTest(selector: (LookupElement) -> Boolean,
                                            expectedDefined: List<Pair<String, MLFeatureValue>>,
                                            expectedUndefined: List<String>) {
    doWithInstalledProviders { _, elementFeaturesProvider ->
      invokeCompletion()

      val selected = myFixture.lookupElements!!.find(selector)
      assertNotNull(selected)

      val features = elementFeaturesProvider.features[selected]
      assertNotNull(features)
      assertHasFeatures(features!!, expectedDefined)
      assertHasNotFeatures(features, expectedUndefined)
    }
  }

  fun doWithInstalledProviders(action: (contextFeaturesProvider: PyAdapterContextFeatureProvider,
                                                elementFeaturesProvider: PyAdapterElementFeatureProvider) -> Unit) {
    val contextFeaturesProvider = PyAdapterContextFeatureProvider(PyContextFeatureProvider())
    val elementFeaturesProvider = PyAdapterElementFeatureProvider(PyElementFeatureProvider())
    try {
      ContextFeatureProvider.EP_NAME.addExplicitExtension(PythonLanguage.INSTANCE, contextFeaturesProvider)
      ElementFeatureProvider.EP_NAME.addExplicitExtension(PythonLanguage.INSTANCE, elementFeaturesProvider)
      action(contextFeaturesProvider, elementFeaturesProvider)
    }
    finally {
      ContextFeatureProvider.EP_NAME.removeExplicitExtension(PythonLanguage.INSTANCE, contextFeaturesProvider)
      ElementFeatureProvider.EP_NAME.removeExplicitExtension(PythonLanguage.INSTANCE, elementFeaturesProvider)
    }
  }

  fun kwId(kw: String) = PyMlCompletionHelpers.getKeywordId(kw)!!

  fun invokeCompletion() {
    myFixture.configureByFile(getTestName(true) + ".py")
    myFixture.completeBasic()
  }

  fun assertHasFeatures(actual: Map<String, MLFeatureValue>,
                                expectedDefined: List<Pair<String, MLFeatureValue>>) {
    for (pair in expectedDefined) {
      Assert.assertTrue("Assert has feature: ${pair.first}", actual.containsKey(pair.first))
      Assert.assertEquals("Check feature value: ${pair.first}", pair.second.toString(), actual[pair.first].toString())
    }
  }

  fun assertHasNotFeatures(actual: Map<String, MLFeatureValue>, expected: List<String>) {
    for (value in expected) {
      Assert.assertFalse("Assert has not feature: $value", actual.containsKey(value))
    }
  }
}