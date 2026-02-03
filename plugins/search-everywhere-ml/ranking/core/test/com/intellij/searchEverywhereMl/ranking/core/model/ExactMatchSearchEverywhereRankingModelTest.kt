package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereRankingModelTest
import com.intellij.testFramework.VfsTestUtil
import org.junit.Assert

internal class ExactMatchSearchEverywhereRankingModelTest : SearchEverywhereRankingModelTest() {
  private val nonExactMatchValue = 0.9
  private val exactMatchValue = 0.99
  private val exactMatchWithExtensionValue = 1.0

  override val tab: SearchEverywhereTab.TabWithMlRanking
    get() = throw NotImplementedError() // Not needed.

  override fun filterElements(searchQuery: String): List<FoundItemDescriptor<*>> {
    val elements = mutableListOf<FoundItemDescriptor<*>>()
    gotoFileModelProvider.filterElementsWithWeights(viewModel, searchQuery, false, mockProgressIndicator) {
      elements.add(it)
      true
    }
    return elements
  }


  private val gotoFileModel by lazy { GotoFileModel(project) }
  private val gotoFileModelProvider: GotoFileItemProvider by lazy { gotoFileModel.getItemProvider(null) as GotoFileItemProvider }
  private val viewModel by lazy { StubChooseByNameViewModel(gotoFileModel) }
  override val model = ExactMatchSearchEverywhereRankingModel(
    object : DecisionFunction {
      override fun getFeaturesOrder(): Array<FeatureMapper> {
        return emptyArray()
      }

      override fun getRequiredFeatures(): MutableList<String> {
        return mutableListOf()
      }

      override fun getUnknownFeatures(features: MutableCollection<String>): MutableList<String> {
        return mutableListOf()
      }

      override fun version(): String {
        return ""
      }

      override fun predict(features: DoubleArray?): Double {
        return 1.0
      }
    })

  private fun assertPredictionEquals(expected: MutableMap<String, Double>, searchQuery: String) {
    VfsTestUtil.syncRefresh()
    lateinit var rankedElements: Map<FoundItemDescriptor<*>, Double>
    ProgressManager.getInstance().executeNonCancelableSection {
      rankedElements = filterElements(searchQuery)
        .associateWith { getMlWeight(it, searchQuery, null) }
    }

    assertEquals("Not all created items were found", rankedElements.size, expected.size)

    // Now let's check the predictions for each file.
    rankedElements.entries.forEach {
      val fileName = (it.key.item as PsiFile).name
      Assert.assertEquals("Prediction for $fileName doesn't match expected value for query $searchQuery.",
                          expected[fileName]!!,
                          it.value,
                          1e-4)
    }
  }

  fun createFiles(fileNames: Collection<String>) {
    module {
      source {
        createPackage("psi.codeStyle") {
          fileNames.forEach { file(it) }
        }
      }
    }
  }

  fun `test exact match works case-insensitive when it should`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "MinusculeMatcher.java" to exactMatchWithExtensionValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "MinusculeMatcher.java")
    assertPredictionEquals(files, "minusculematcher.java")
    assertPredictionEquals(files, "MINUSCULEMATCHER.JAVA")
  }

  fun `test exact match works case-sensitive when it should`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "Minuscules.java" to nonExactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "MinusculeS.java")
  }

  fun `test exact match without extension works`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "MinusculeMatcher.java" to exactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "MinusculeMatcher")
    assertPredictionEquals(files, "MinusculeMatcher.ja")
    assertPredictionEquals(files, "MinusculeMatcher.")
  }

  fun `test exact match doesn't fire when it shouldn't`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "MinusculeMatcher.java" to nonExactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "MinusculeM.java")
    assertPredictionEquals(files, "MinusculeM")
    assertPredictionEquals(files, "Minuscule")
    assertPredictionEquals(files, "MM.java")
    assertPredictionEquals(files, "MM")
  }

  fun `test example from IJPL-61971`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "Pet.java" to exactMatchWithExtensionValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "Pet.java")
    assertPredictionEquals(files, "pet.java")
    assertPredictionEquals(files, "PET.JAVA")
  }

  fun `test examples from documentation1`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "filter.java" to exactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "filter")
    assertPredictionEquals(files, "Filter")
  }

  fun `test examples from documentation2`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "Filter.java" to exactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "filter")
  }

  fun `test examples from documentation3`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "FilterSearch.java" to exactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "filtersearch")
    // assertPredictionEquals(files, "filter_search") // - Is not found by the files tab search filter.
  }

  fun `test examples from documentation4`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "Filters.java" to exactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "Filters")
  }

  fun `test examples from documentation5`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "FilterS.java" to exactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "Filters")
  }

  fun `test examples from documentation6`() {
    val files: MutableMap<String, Double> = mutableMapOf(
      "filters.java" to nonExactMatchValue,
    )
    createFiles(files.keys)
    assertPredictionEquals(files, "FilterS")
  }
}
