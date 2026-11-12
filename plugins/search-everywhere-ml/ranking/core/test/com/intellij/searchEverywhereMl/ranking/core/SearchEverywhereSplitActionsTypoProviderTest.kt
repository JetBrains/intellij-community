package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionItem
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsFilter
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsProvider
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsProviderFactory
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
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
  fun `actions factory creates the actions provider`() = runBlocking {
    val provider = SeActionsProviderFactory().getItemsProvider(project = null, DataContext.EMPTY_CONTEXT)
    try {
      assertInstanceOf(SeActionsProvider::class.java, provider)
    }
    finally {
      Disposer.dispose(provider)
    }
  }

  @Test
  fun `all tab provider keeps typo tolerance and recent actions out`() = runBlocking {
    val query = "show colr piker"
    val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91)
    spellingCorrector.correctionsByQuery[query] = listOf(correction)
    val resultsByQuery = mapOf(
      query to listOf(createMatchedValue("original-hit")),
      correction.correction to listOf(createMatchedValue("corrected-hit")),
    )

    val allTabProvider = TestActionsProvider(resultsByQuery)
    collectItems(allTabProvider, query, isAllTab = true)
    assertEquals(listOf(query), allTabProvider.fetchedQueries)
    assertEquals(listOf(false), allTabProvider.includeRecentValues)
    assertTrue(spellingCorrector.requestedQueries.isEmpty())

    val separateTabProvider = TestActionsProvider(resultsByQuery)
    collectItems(separateTabProvider, query)
    assertEquals(listOf(query), spellingCorrector.requestedQueries)
    assertEquals(setOf(query, correction.correction), separateTabProvider.fetchedQueries.toSet())
    assertEquals(listOf(true, true), separateTabProvider.includeRecentValues)
  }

  @Test
  fun `typo tolerant provider requests unique corrected queries`() = runBlocking {
    val correction = SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91)
    spellingCorrector.correctionsByQuery["show colr piker"] = listOf(
      SearchEverywhereSpellCheckResult.Correction("show colr piker", 0.99),
      correction,
      SearchEverywhereSpellCheckResult.Correction("show color picker", 0.87),
    )

    val provider = TestActionsProvider(
      resultsByQuery = mapOf(
        "show colr piker" to listOf(createMatchedValue("original-hit")),
        "show color picker" to listOf(createMatchedValue("corrected-hit")),
      ),
    )

    val items = collectItems(provider, "show colr piker")

    assertEquals(listOf("show colr piker"), spellingCorrector.requestedQueries)
    assertEquals(setOf("show colr piker", "show color picker"), provider.fetchedQueries.toSet())
    assertEquals(2, provider.fetchedQueries.size)
    assertEquals(setOf("show colr piker", "show color picker"), items.map { it.effectiveSearchText }.toSet())
    assertEquals(
      setOf(SearchEverywhereSpellCheckResult.NoCorrection, correction),
      items.map { it.correction }.toSet(),
    )
  }

  @Test
  fun `typo tolerant provider selects corrected item with corrected query`() = runBlocking {
    spellingCorrector.correctionsByQuery["show colr piker"] = listOf(SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91))

    val provider = TestActionsProvider(
      resultsByQuery = mapOf(
        "show color picker" to listOf(createMatchedValue("corrected-hit")),
      ),
    )

    val item = collectItems(provider, "show colr piker").single()

    provider.itemSelected(item, modifiers = 0, searchText = "show colr piker")

    assertEquals(listOf("show color picker"), provider.selectedQueries)
  }

  @Test
  fun `typo tolerant provider starts corrected query before original completes`() = runBlocking {
    val originalStarted = CountDownLatch(1)
    val releaseOriginal = CountDownLatch(1)
    val correctedStarted = CountDownLatch(1)
    spellingCorrector.correctionsByQuery["show colr piker"] = listOf(SearchEverywhereSpellCheckResult.Correction("show color picker", 0.91))

    val provider = object : TestActionsProvider(
      resultsByQuery = mapOf(
        "show color picker" to listOf(createMatchedValue("corrected-hit")),
      ),
    ) {
      override suspend fun findItems(
        pattern: String,
        includeDisabled: Boolean,
        includeRecent: Boolean,
        consumer: suspend (GotoActionModel.MatchedValue, Int) -> Boolean,
      ) {
        when (pattern) {
          "show colr piker" -> {
            originalStarted.countDown()
            assertTrue(releaseOriginal.await(5, TimeUnit.SECONDS))
          }
          "show color picker" -> correctedStarted.countDown()
        }
        super.findItems(pattern, includeDisabled, includeRecent, consumer)
      }
    }

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

    val provider = TestActionsProvider(
      resultsByQuery = mapOf(
        "show colr piker" to listOf(sharedHit),
        "show color picker" to listOf(sharedHit),
      ),
    )

    val items = collectItems(provider, "show colr piker")

    assertEquals(2, items.size)
    assertEquals(listOf("shared-hit", "shared-hit"), items.map { it.matchedValue.valueText.orEmpty() }.sorted())
    assertEquals(1, items.count { it.correction == SearchEverywhereSpellCheckResult.NoCorrection })
    assertEquals(1, items.count { it.correction == correction })
  }

  @Test
  fun `plain provider does not request corrections and uses live search text on selection`() = runBlocking {
    spellingCorrector.correctionsByQuery["sho"] = listOf(SearchEverywhereSpellCheckResult.Correction("show", 0.91))

    val provider = TestActionsProvider(
      resultsByQuery = mapOf(
        "sho" to listOf(createMatchedValue("original-hit")),
      ),
    )

    val item = collectItems(provider, "sho", isAllTab = true).single()
    provider.itemSelected(item, modifiers = 0, searchText = "show")

    assertTrue(spellingCorrector.requestedQueries.isEmpty())
    assertEquals(listOf("sho"), provider.fetchedQueries)
    assertEquals(listOf("show"), provider.selectedQueries)
  }

  /**
   * Collects the items of [query]. The filter state tells the provider the tab, as the All tab and the Actions tab do.
   */
  private suspend fun collectItems(provider: SeItemsProvider, query: String, isAllTab: Boolean = false): List<SeActionItem> {
    val filter = if (isAllTab) SeEverywhereFilterImpl(isAllTab = true, isEverywhere = false, disabledProviderIds = emptyList())
    else SeActionsFilter(includeDisabled = false, isAutoTogglePossible = false)
    val result = mutableListOf<SeActionItem>()
    provider.collectItems(SeParams(query, filter.toState())) { item ->
      result += item as SeActionItem
      true
    }
    return result
  }

  private fun createMatchedValue(name: String): GotoActionModel.MatchedValue {
    val action = object : AnAction(name) {
      override fun actionPerformed(e: AnActionEvent) {
      }
    }
    val wrapper = GotoActionModel.ActionWrapper(action, null, MatchMode.NAME, Presentation().apply { text = name })
    return GotoActionModel.MatchedValue(wrapper, name, GotoActionModel.MatchedValueType.ACTION)
  }

  private open class TestActionsProvider(
    private val resultsByQuery: Map<String, List<GotoActionModel.MatchedValue>>,
  ) : SeActionsProvider(project = null, contextComponent = null, editor = null, dataContext = null) {
    val fetchedQueries: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val includeRecentValues: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())
    val selectedQueries: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override suspend fun findItems(
      pattern: String,
      includeDisabled: Boolean,
      includeRecent: Boolean,
      consumer: suspend (GotoActionModel.MatchedValue, Int) -> Boolean,
    ) {
      fetchedQueries += pattern
      includeRecentValues += includeRecent
      resultsByQuery[pattern].orEmpty().forEachIndexed { index, matchedValue ->
        consumer(matchedValue, 100 - index)
      }
    }

    override fun processSelectedItem(item: GotoActionModel.MatchedValue, modifiers: Int, searchText: String): Boolean {
      selectedQueries += searchText
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
