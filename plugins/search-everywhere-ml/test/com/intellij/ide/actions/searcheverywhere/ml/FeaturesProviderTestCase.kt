package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ml.FeaturesProviderTestCase.AssertionElementSelector.AssertionSpecifier
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

internal abstract class FeaturesProviderTestCase<T : SearchEverywhereElementFeaturesProvider>(private val providerClass: Class<T>)
  : LightJavaCodeInsightFixtureTestCase() {
  protected val provider: SearchEverywhereElementFeaturesProvider by lazy {
    SearchEverywhereElementFeaturesProvider.EP_NAME.findExtensionOrFail(providerClass)
  }

  override fun getTestDataPath(): String {
    return PluginPathManager.getPluginHomePath("search-everywhere-ml").plus("/testData")
  }

  protected fun getFileFromTestData(name: String): PsiFile {
    val vf = getFileFromPath(name)
    return psiManager.findFile(vf)!!
  }

  protected fun getDirectoryFromTestData(name: String): PsiDirectory {
    val vf = getFileFromPath(name)
    return psiManager.findDirectory(vf)!!
  }

  private fun getFileFromPath(name: String) = LocalFileSystem.getInstance().findFileByPath("$testDataPath/$name")!!

  fun checkThatFeature(feature: String) = AssertionElementSelector(feature)

  fun checkThatFeatures() = AssertionElementSelector(null)

  /**
   * The [AssertionElementSelector] and its inner classes allow for writing concise
   * tests with methods chaining, that takes care of checking a feature
   *   - has a certain value (through [AssertionSpecifier.isEqualTo]), or
   *   - its value changes (through [AssertionSpecifier.changes]) upon some operation
   *
   * To use it, use either [checkThatFeature] to perform assertion on a single feature,
   * or [checkThatFeatures] to perform assertion on multiple features, passed as a
   * Map<String, *> to either [AssertionSpecifier.isEqualTo] or [AssertionSpecifier.changes]
   *
   * By default, all other arguments passed to [SearchEverywhereElementFeaturesProvider.getElementFeatures]
   * are equal to 0, apart from the element. To change that and pass a different value for testing, use:
   *   - [AssertionSpecifier.withCurrentTime]
   *   - [AssertionSpecifier.withQueryLength]
   *   - [AssertionSpecifier.withPriority]
   */
  inner class AssertionElementSelector(private val feature: String?) {
    fun <E : Any> ofElement(element: E) = AssertionSpecifier(element)

    inner class AssertionSpecifier<E : Any>(private val element: E) {
      private var features: Map<String, Any> = emptyMap()
      private var currentTime = 0L
      private var queryLength = 0
      private var elementPriority = 0
      private val cache = provider.getDataToCache(project)

      /**
       * Specifies the current time / session start time that will be passed when obtaining the features,
       * see [SearchEverywhereElementFeaturesProvider.getElementFeatures]
       */
      fun withCurrentTime(time: Long): AssertionSpecifier<E> {
        currentTime = time
        return this
      }

      /**
       * Specifies the query length start time that will be passed when obtaining the features,
       * see [SearchEverywhereElementFeaturesProvider.getElementFeatures]
       */
      fun withQueryLength(length: Int): AssertionSpecifier<E> {
        queryLength = length
        return this
      }

      /**
       * Specifies the priority of the element that will be passed when obtaining the features,
       * see [SearchEverywhereElementFeaturesProvider.getElementFeatures]
       */
      fun withPriority(priority: Int): AssertionSpecifier<E> {
        elementPriority = priority
        return this
      }

      /**
       * Checks if the specified [feature] of the element has the [expectedValue]
       */
      fun isEqualTo(expectedValue: Any?) = assert(expectedValue)

      /**
       * Checks if the value of the [feature] will change from [from] to [to] after the specified operation is performed.
       *
       * @see ChangeOperation.after
       */
      fun changes(from: Any?, to: Any?) = ChangeOperation(from, to)

      private fun assert(expectedValue: Any?) {
        features = provider.getElementFeatures(element, currentTime, queryLength, elementPriority, cache)

        if (features.isEmpty()) {
          fail("The provider ${providerClass.simpleName} has returned empty map of features. Maybe element type is incorrect?")
          return
        }

        when (feature) {
          null -> assertMultipleFeatures(expectedValue)
          else -> assertSingleFeature(feature, expectedValue)
        }
      }

      private fun assertSingleFeature(key: String, expectedValue: Any?) {
        val actualValue = features[key]
        assertEquals("Assertion of the feature $key has failed", expectedValue, actualValue)
      }

      private fun assertMultipleFeatures(expectedValue: Any?) {
        if (expectedValue == null) {
          throw IllegalArgumentException("The expected value for checkThatFeatures() must be of type Map<String, *>, not null")
        }
        else if (expectedValue !is Map<*, *>) {
          throw IllegalArgumentException("The expected value for checkThatFeatures() must be of type Map<String, *>")
        }

        expectedValue.forEach { (key, value) ->
          key as String
          assertSingleFeature(key, value)
        }
      }

      inner class ChangeOperation(private val oldValue: Any?, private val newValue: Any?) {
        /**
         * Checks if the value of the feature will change after [operation] is performed.
         */
        fun after(operation: (E) -> Unit) {
          assert(oldValue)
          operation(element)
          assert(newValue)
        }
      }
    }
  }
}

