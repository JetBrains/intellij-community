package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionItem
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsAdaptedProvider
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsProviderFactory
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Processor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestApplication
internal class SearchEverywhereSplitActionsTypoProviderTest {
  companion object {
    private val spellingCorrector = TestSpellingCorrector()
    private val classDisposable = Disposer.newDisposable()

    @JvmStatic
    @BeforeAll
    fun setUpClass() {
      ExtensionTestUtil.maskExtensions(
        ExtensionPointName.create<SearchEverywhereSpellingCorrectorFactory>("com.intellij.searchEverywhereSpellingCorrector"),
        listOf(TestSpellingCorrectorFactory(spellingCorrector)),
        classDisposable,
      )
    }

    @JvmStatic
    @AfterAll
    fun tearDownClass() {
      Disposer.dispose(classDisposable)
    }
  }

  @BeforeEach
  fun setUp() {
    spellingCorrector.reset()
  }

  @Test
  fun `actions factory keeps typo tolerance out of all tab`() {
    runBlocking {
      val query = "show colr piker"
      val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91)
      spellingCorrector.correctionsByQuery[query] = listOf(correction)

      val allTabContributor = TestActionContributor(
        resultsByQuery = mapOf(
          query to listOf(createMatchedValue("original-hit")),
          correction.correction to listOf(createMatchedValue("corrected-hit")),
        ),
      )
      val separateTabContributor = TestActionContributor(
        resultsByQuery = mapOf(
          query to listOf(createMatchedValue("original-hit")),
          correction.correction to listOf(createMatchedValue("corrected-hit")),
        ),
      )
      val factory = SeActionsProviderFactory()

      val allTabProvider = factory.getItemsProvider(project = null, legacyContributor = allTabContributor.asAnyContributor(), isAllTab = true)
      val separateTabProvider = factory.getItemsProvider(project = null, legacyContributor = separateTabContributor.asAnyContributor(), isAllTab = false)

      assertInstanceOf(SeActionsAdaptedProvider::class.java, allTabProvider)
      assertInstanceOf(SeActionsAdaptedProvider::class.java, separateTabProvider)

      collectItems(requireNotNull(allTabProvider), query)
      assertEquals(listOf(query), allTabContributor.fetchedQueries)
      assertTrue(spellingCorrector.requestedQueries.isEmpty())

      collectItems(requireNotNull(separateTabProvider), query)
      assertEquals(listOf(query), spellingCorrector.requestedQueries)
      assertEquals(setOf(query, correction.correction), separateTabContributor.fetchedQueries.toSet())
    }
  }

  @Test
  fun `typo tolerant provider requests unique corrected queries`() = runBlocking {
    val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91)
    spellingCorrector.correctionsByQuery["show colr piker"] = listOf(
      SearchEverywhereSpellCheckResult.Correction("show colr piker", 0.99),
      correction,
      SearchEverywhereSpellCheckResult.Correction("show color picker", 0.87),
    )

    val contributor = TestActionContributor(
      resultsByQuery = mapOf(
        "show colr piker" to listOf(createMatchedValue("original-hit")),
        "show color picker" to listOf(createMatchedValue("corrected-hit")),
      ),
    )
    val provider = createProvider(contributor, isTypoTolerantSearchEnabled = true)

    val items = collectItems(provider, "show colr piker")

    assertEquals(listOf("show colr piker"), spellingCorrector.requestedQueries)
    assertEquals(setOf("show colr piker", "show color picker"), contributor.fetchedQueries.toSet())
    assertEquals(2, contributor.fetchedQueries.size)
    assertEquals(setOf("show colr piker", "show color picker"), items.map { it.effectiveSearchText }.toSet())
    assertEquals(
      setOf(SearchEverywhereSpellCheckResult.NoCorrection, correction),
      items.map { it.correction }.toSet(),
    )
  }

  @Test
  fun `typo tolerant provider selects corrected item with corrected query`() = runBlocking {
    spellingCorrector.correctionsByQuery["show colr piker"] = listOf(SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91))

    val contributor = TestActionContributor(
      resultsByQuery = mapOf(
        "show color picker" to listOf(createMatchedValue("corrected-hit")),
      ),
    )
    val provider = createProvider(contributor, isTypoTolerantSearchEnabled = true)

    val item = collectItems(provider, "show colr piker").single()

    provider.itemSelected(item, modifiers = 0, searchText = "show colr piker")

    assertEquals(listOf("show color picker"), contributor.selectedQueries)
  }

  @Test
  fun `typo tolerant provider starts corrected query before original completes`() = runBlocking {
    val originalStarted = CountDownLatch(1)
    val releaseOriginal = CountDownLatch(1)
    val correctedStarted = CountDownLatch(1)
    spellingCorrector.correctionsByQuery["show colr piker"] = listOf(SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91))

    val contributor = object : TestActionContributor(
      resultsByQuery = mapOf(
        "show color picker" to listOf(createMatchedValue("corrected-hit")),
      ),
    ) {
      override fun fetchWeightedElements(pattern: String,
                                         progressIndicator: ProgressIndicator,
                                         consumer: Processor<in FoundItemDescriptor<GotoActionModel.MatchedValue>>) {
        fetchedQueries += pattern
        when (pattern) {
          "show colr piker" -> {
            originalStarted.countDown()
            assertTrue(releaseOriginal.await(5, TimeUnit.SECONDS))
          }
          "show color picker" -> correctedStarted.countDown()
        }

        resultsByQuery[pattern].orEmpty().forEachIndexed { index, matchedValue ->
          consumer.process(FoundItemDescriptor(matchedValue, 100 - index))
        }
      }
    }
    val provider = createProvider(contributor, isTypoTolerantSearchEnabled = true)

    val collectJob = launch(Dispatchers.Default) {
      collectItems(provider, "show colr piker")
    }

    assertTrue(originalStarted.await(5, TimeUnit.SECONDS))
    assertTrue(correctedStarted.await(5, TimeUnit.SECONDS))
    releaseOriginal.countDown()
    collectJob.join()
  }

  @Test
  fun `typo tolerant provider leaves duplicate replacement to equality phase`() = runBlocking {
    val sharedHit = createMatchedValue("shared-hit")
    val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91)
    spellingCorrector.correctionsByQuery["show colr piker"] = listOf(correction)

    val contributor = TestActionContributor(
      resultsByQuery = mapOf(
        "show colr piker" to listOf(sharedHit),
        "show color picker" to listOf(sharedHit),
      ),
    )
    val provider = createProvider(contributor, isTypoTolerantSearchEnabled = true)

    val items = collectItems(provider, "show colr piker")

    assertEquals(2, items.size)
    assertEquals(listOf("shared-hit", "shared-hit"), items.map { it.matchedValue.valueText.orEmpty() }.sorted())
    assertEquals(1, items.count { it.correction == SearchEverywhereSpellCheckResult.NoCorrection })
    assertEquals(1, items.count { it.correction == correction })
  }

  @Test
  fun `plain provider does not request corrections and uses live search text on selection`() = runBlocking {
    spellingCorrector.correctionsByQuery["sho"] = listOf(SearchEverywhereSpellCheckResult.Correction("show", 0.91))

    val contributor = TestActionContributor(
      resultsByQuery = mapOf(
        "sho" to listOf(createMatchedValue("original-hit")),
      ),
    )
    val provider = createProvider(contributor, isTypoTolerantSearchEnabled = false)

    val item = collectItems(provider, "sho").single()
    provider.itemSelected(item, modifiers = 0, searchText = "show")

    assertTrue(spellingCorrector.requestedQueries.isEmpty())
    assertEquals(listOf("sho"), contributor.fetchedQueries)
    assertEquals(listOf("show"), contributor.selectedQueries)
  }

  private fun createProvider(
    contributor: TestActionContributor,
    isTypoTolerantSearchEnabled: Boolean,
  ): SeActionsAdaptedProvider {
    return SeActionsAdaptedProvider(
      SeAsyncContributorWrapper(contributor),
      isTypoTolerantSearchEnabled = isTypoTolerantSearchEnabled,
    )
  }

  private suspend fun collectItems(provider: SeItemsProvider, query: String): List<SeActionItem> {
    val result = mutableListOf<SeActionItem>()
    provider.collectItems(SeParams(query, SeFilterState.Empty)) { item ->
      result += item as SeActionItem
      true
    }
    return result
  }

  @Suppress("UNCHECKED_CAST")
  private fun TestActionContributor.asAnyContributor(): SearchEverywhereContributor<Any> = this as SearchEverywhereContributor<Any>

  private fun createMatchedValue(name: String): GotoActionModel.MatchedValue {
    val action = object : AnAction(name) {
      override fun actionPerformed(e: AnActionEvent) {
      }
    }
    val wrapper = GotoActionModel.ActionWrapper(action, null, MatchMode.NAME, Presentation().apply { text = name })
    return GotoActionModel.MatchedValue(wrapper, name, GotoActionModel.MatchedValueType.ACTION)
  }

  private open class TestActionContributor(
    protected val resultsByQuery: Map<String, List<GotoActionModel.MatchedValue>> = emptyMap(),
  ) : ActionSearchEverywhereContributor(project = null, contextComponent = null, editor = null, dataContext = null) {
    val fetchedQueries: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val selectedQueries: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun getActions(onChanged: Runnable): List<AnAction> = emptyList()

    override fun fetchWeightedElements(pattern: String,
                                       progressIndicator: ProgressIndicator,
                                       consumer: Processor<in FoundItemDescriptor<GotoActionModel.MatchedValue>>) {
      fetchedQueries += pattern
      resultsByQuery[pattern].orEmpty().forEachIndexed { index, matchedValue ->
        consumer.process(FoundItemDescriptor(matchedValue, 100 - index))
      }
    }

    override fun processSelectedItem(item: GotoActionModel.MatchedValue, modifiers: Int, text: String): Boolean {
      selectedQueries += text
      return true
    }
  }

  private class TestSpellingCorrector : SearchEverywhereSpellingCorrector {
    val correctionsByQuery: MutableMap<String, List<SearchEverywhereSpellCheckResult.Correction>> = mutableMapOf()
    val requestedQueries: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun isAvailableInTab(tabId: String): Boolean {
      return tabId == ActionSearchEverywhereContributor::class.java.simpleName
    }

    override fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult {
      return getAllCorrections(query, 1).firstOrNull() ?: SearchEverywhereSpellCheckResult.NoCorrection
    }

    override fun getAllCorrections(query: String, maxCorrections: Int): List<SearchEverywhereSpellCheckResult.Correction> {
      requestedQueries += query
      return correctionsByQuery[query].orEmpty().take(maxCorrections)
    }

    fun reset() {
      correctionsByQuery.clear()
      requestedQueries.clear()
    }
  }

  private class TestSpellingCorrectorFactory(
    private val spellingCorrector: SearchEverywhereSpellingCorrector,
  ) : SearchEverywhereSpellingCorrectorFactory {
    override fun isAvailable(): Boolean = true

    override fun create(): SearchEverywhereSpellingCorrector = spellingCorrector
  }
}
