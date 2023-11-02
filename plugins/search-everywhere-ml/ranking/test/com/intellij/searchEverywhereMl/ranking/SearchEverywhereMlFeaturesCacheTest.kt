package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereActionFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereCommonFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_INFO_ID
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_IS_MOST_POPULAR
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_POPULARITY_INDEX
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContributorFeaturesProvider.Companion.CONTRIBUTOR_WEIGHT
import com.intellij.searchEverywhereMl.ranking.id.SearchEverywhereMlItemIdProvider
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class SearchEverywhereMlFeaturesCacheTest : HeavyPlatformTestCase() {
  private class MockElementInfoBuilder {
    private var element: Any? = null
    private var priority: Int? = null
    private var contributor: SearchEverywhereContributor<*>? = null
    private val mlFeatures = mutableListOf<EventPair<*>>()
    private var mlWeight: Double? = null

    fun withPriority(priority: Int) = apply {
      this.priority = priority
      mlFeatures.add(SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(priority))
    }

    fun withTotalSymbolsAmount(totalSymbolsAmount: Int) = apply {
      mlFeatures.add(SearchEverywhereActionFeaturesProvider.Fields.TEXT_LENGTH_KEY.with(totalSymbolsAmount))
    }

    fun withIsAction(isAction: Boolean) = apply {
      mlFeatures.add(SearchEverywhereActionFeaturesProvider.Fields.IS_ACTION_DATA_KEY.with(isAction))
    }

    fun withUsage(usage: Int) = apply {
      mlFeatures.add(SearchEverywhereActionFeaturesProvider.Fields.USAGE.with(usage))
    }

    fun withElement(element: Any) = apply {
      this.element = element
    }

    fun withContributor(contributor: SearchEverywhereContributor<*>) = apply {
      this.contributor = contributor
    }

    fun withContributorId(contributorId: String) = apply {
      this.contributor = MockSearchEverywhereContributor(contributorId)
    }

    fun withMlWeight(mlWeight: Double) = apply {
      this.mlWeight = mlWeight
    }

    fun build(): SearchEverywhereFoundElementInfoWithMl {
      return SearchEverywhereFoundElementInfoWithMl(
        element ?: Any(),
        priority ?: 0,
        contributor ?: MockSearchEverywhereContributor(),
        mlWeight,
        mlFeatures)
    }

  }

  private class MockItemIdProvider : SearchEverywhereMlItemIdProvider {
    private val itemToId = hashMapOf<Any, Int>()
    private var maxId = 0

    fun setId(element: Any, id: Int?) {
      if (id == null) {
        itemToId[element] = maxId + 1
        maxId += 1
      }
      else {
        itemToId[element] = id
      }
    }

    override fun getId(element: Any): Int? {
      return itemToId[element]
    }
  }

  companion object {
    private fun getContributorFeatures(element: SearchEverywhereFoundElementInfoWithMl): List<EventPair<*>> {
      return getContributorFeatures(element.contributor.searchProviderId)
    }

    internal fun getContributorFeatures(contributorId: String): List<EventPair<*>> {
      /*
      EventPair(field=ValidatedByAllowedValues(name=contributorId, allowedValues=[SearchEverywhereContributor.All, ClassSearchEverywhereContributor, FileSearchEverywhereContributor, RecentFilesSEContributor, SymbolSearchEverywhereContributor, ActionSearchEverywhereContributor, RunConfigurationsSEContributor, CommandsContributor, TopHitSEContributor, com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor, TmsSearchEverywhereContributor, YAMLKeysSearchEverywhereContributor, UrlSearchEverywhereContributor, Vcs.Git, AutocompletionContributor, TextSearchContributor, DbSETablesContributor, third.party]), data=ClassSearchEverywhereContributor)
       */
      return when (contributorId) {
        "ClassSearchEverywhereContributor" -> listOf(
          CONTRIBUTOR_INFO_ID.with("ClassSearchEverywhereContributor"),
          CONTRIBUTOR_WEIGHT.with(100),
          CONTRIBUTOR_IS_MOST_POPULAR.with(false),
          CONTRIBUTOR_POPULARITY_INDEX.with(3)
        )
        "FileSearchEverywhereContributor" -> listOf(
          CONTRIBUTOR_INFO_ID.with("FileSearchEverywhereContributor"),
          CONTRIBUTOR_WEIGHT.with(200),
          CONTRIBUTOR_IS_MOST_POPULAR.with(true),
          CONTRIBUTOR_POPULARITY_INDEX.with(0)
        )
        "RecentFilesSEContributor" -> listOf(
          CONTRIBUTOR_INFO_ID.with("RecentFilesSEContributor"),
          CONTRIBUTOR_WEIGHT.with(70),
          CONTRIBUTOR_IS_MOST_POPULAR.with(false),
          CONTRIBUTOR_POPULARITY_INDEX.with(1)
        )
        "ActionSearchEverywhereContributor" -> listOf(
          CONTRIBUTOR_INFO_ID.with("ActionSearchEverywhereContributor"),
          CONTRIBUTOR_WEIGHT.with(400),
          CONTRIBUTOR_IS_MOST_POPULAR.with(false),
          CONTRIBUTOR_POPULARITY_INDEX.with(2)
        )
        else -> {
          emptyList()
        }
      }
    }
  }


  private fun assertEventIds(events: List<ObjectEventData>?, ids: List<Int>, changedIds: List<Int>) {
    if (ids.isEmpty()) {
      TestCase.assertNull(events)
      return
    }
    TestCase.assertNotNull(events)

    val allowedFields = listOf(SearchEverywhereMLStatisticsCollector.FEATURES_DATA_KEY,
                               SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY,
                               SearchEverywhereMLStatisticsCollector.ML_WEIGHT_KEY,
                               SearchEverywhereMLStatisticsCollector.ID_KEY,
                               SearchEverywhereMLStatisticsCollector.CONTRIBUTOR_DATA_KEY)

    UsefulTestCase.assertOrderedEquals(events?.map {
      it.buildObjectData(MLSE_RECORDER_ID, allowedFields.toTypedArray())["id"]
    } ?: emptyList(), ids)

    UsefulTestCase.assertOrderedEquals(
      events?.map {
        it.buildObjectData(MLSE_RECORDER_ID, allowedFields.toTypedArray())
      }
        ?.filter { it.size > 1 }
        ?.map { it["id"] }
      ?: emptyList(), changedIds
    )
  }

  private fun assertFeatures(events: List<ObjectEventData>, commonFeatures: List<List<Pair<String, Any>>>?,
                             mlFeatures: List<List<Pair<String, Any>>>?) {
    val allowedFields = listOf(SearchEverywhereMLStatisticsCollector.FEATURES_DATA_KEY,
                               SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY,
                               SearchEverywhereMLStatisticsCollector.ML_WEIGHT_KEY,
                               SearchEverywhereMLStatisticsCollector.CONTRIBUTOR_DATA_KEY,
                               SearchEverywhereMLStatisticsCollector.ID_KEY)

    val mappedEvents = events.map {
      it.buildObjectData(MLSE_RECORDER_ID, allowedFields.toTypedArray())
    }

    commonFeatures?.zip(mappedEvents)?.forEach { (elementsFeatures, mappedElementEvents) ->
      elementsFeatures.forEach {
        TestCase.assertEquals(it.second, mappedElementEvents[it.first])
      }
    }

    mlFeatures?.zip(mappedEvents)?.forEach { (elementsFeatures, mappedElementEvents) ->
      elementsFeatures.forEach {
        TestCase.assertEquals(it.second, (mappedElementEvents["features"] as HashMap<*, *>)[it.first])
      }
    }

  }

  fun `test unchanged items are not logged`() {
    val featuresCache = SearchEverywhereMlFeaturesCache()
    val idProvider = MockItemIdProvider()
    val elementsFirstSearch = listOf(
      MockElementInfoBuilder()
        .withPriority(1011)
        .withUsage(11)
        .withMlWeight(600000.0)
        .withContributorId("ActionSearchEverywhereContributor")
        .withIsAction(true)
        .build(),
      MockElementInfoBuilder()
        .withPriority(1010)
        .withUsage(9)
        .withMlWeight(50000.0)
        .withContributorId("ActionSearchEverywhereContributor")
        .withIsAction(true)
        .build(),
      MockElementInfoBuilder()
        .withPriority(1000)
        .withUsage(7)
        .withMlWeight(30000.0)
        .withContributorId("ClassSearchEverywhereContributor")
        .withIsAction(false)
        .build(),
    )

    elementsFirstSearch.forEach { idProvider.setId(it.element, null) }

    val stateFirst = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsFirstSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateFirst, listOf(1, 2, 3), changedIds = listOf(1, 2, 3))

    val newElements = listOf(
      MockElementInfoBuilder()
        .withPriority(100)
        .withUsage(2)
        .withMlWeight(35000.0)
        .withContributorId("ActionSearchEverywhereContributor")
        .withIsAction(true)
        .build(),
      MockElementInfoBuilder()
        .withPriority(900)
        .withUsage(1)
        .withMlWeight(20100.0)
        .withContributorId("ClassSearchEverywhereContributor")
        .withIsAction(false)
        .build(),
    )

    newElements.forEach { idProvider.setId(it.element, null) }

    val elementsSecondSearch = listOf(
      elementsFirstSearch[0],
      newElements[0],
      elementsFirstSearch[1],
      elementsFirstSearch[2],
      newElements[1]
    )

    val stateSecond = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsSecondSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateSecond, listOf(1, 4, 2, 3, 5), changedIds = listOf(4, 5))
  }

  fun `test changed items are logged`() {
    val featuresCache = SearchEverywhereMlFeaturesCache()
    val idProvider = MockItemIdProvider()

    val elementsFirstSearch = listOf(
      MockElementInfoBuilder()
        .withPriority(100)
        .withIsAction(true)
        .withContributorId("ActionSearchEverywhereContributor")
        .withMlWeight(1000.0)
        .withTotalSymbolsAmount(1)
        .build(),
      MockElementInfoBuilder()
        .withPriority(1000)
        .withIsAction(false)
        .withContributorId("ClassSearchEverywhereContributor")
        .withMlWeight(1000.0)
        .withTotalSymbolsAmount(1)
        .build()
    )
    elementsFirstSearch.forEach { idProvider.setId(it.element, null) }

    val stateFirst = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsFirstSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateFirst, listOf(1, 2), changedIds = listOf(1, 2))

    // Changed first and second elements with ids 1 and 2

    val elementsSecondSearch = listOf(
      MockElementInfoBuilder()
        .withPriority(100)
        .withIsAction(true)
        .withContributorId("ActionSearchEverywhereContributor")
        .withMlWeight(1000.0)
        .withTotalSymbolsAmount(2)
        .build(),
      MockElementInfoBuilder()
        .withPriority(1000)
        .withIsAction(false)
        .withContributorId("ClassSearchEverywhereContributor")
        .withMlWeight(1000.0)
        .withTotalSymbolsAmount(2)
        .build()
    )

    // New objects should have the same id
    elementsSecondSearch.forEachIndexed { index, elementInfoWithMl -> idProvider.setId(elementInfoWithMl.element, index + 1) }

    val stateSecond = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsSecondSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateSecond, listOf(1, 2), changedIds = listOf(1, 2))

    // Changed only second element with id 2

    val elementsThirdSearch = listOf(
      elementsSecondSearch[0],
      MockElementInfoBuilder()
        .withPriority(1000)
        .withIsAction(true) // changed false -> true
        .withContributorId("ClassSearchEverywhereContributor")
        .withMlWeight(1000.0)
        .withTotalSymbolsAmount(2)
        .build()
    )

    elementsThirdSearch.forEachIndexed { index, elementInfoWithMl -> idProvider.setId(elementInfoWithMl.element, index + 1) }

    val stateThird = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsThirdSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateThird, listOf(1, 2), changedIds = listOf(2))

    // Changed only first element with id 1
    val elementsFourthSearch = listOf(
      MockElementInfoBuilder()
        .withPriority(100)
        .withIsAction(false) // true -> false
        .withContributorId("ActionSearchEverywhereContributor")
        .withMlWeight(1000.0)
        .withTotalSymbolsAmount(2)
        .build(),
      elementsThirdSearch[1]
    )

    elementsFourthSearch.forEachIndexed { index, elementInfoWithMl -> idProvider.setId(elementInfoWithMl.element, index + 1) }

    val stateFourth = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsFourthSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateFourth, listOf(1, 2), changedIds = listOf(1))
  }

  fun `test new ids and changed items`() {
    val featuresCache = SearchEverywhereMlFeaturesCache()
    val idProvider = MockItemIdProvider()

    val elementsFirstSearch = listOf(
      MockElementInfoBuilder()
        .withMlWeight(100.0)
        .withPriority(1000)
        .withUsage(10)
        .withContributorId("ActionSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(200.0)
        .withPriority(2000)
        .withUsage(20)
        .withContributorId("ActionSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(300.0)
        .withPriority(3000)
        .withUsage(30)
        .withContributorId("ActionSearchEverywhereContributor")
        .build()
    )

    elementsFirstSearch.forEach { idProvider.setId(it.element, null) }

    val stateFirst = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsFirstSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateFirst, listOf(1, 2, 3), changedIds = listOf(1, 2, 3))

    val elementsSecondSearch = listOf(
      elementsFirstSearch[0],
      MockElementInfoBuilder()
        .withMlWeight(200.0)
        .withPriority(2000)
        .withUsage(25) // 20 -> 25
        .withContributorId("ActionSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder() // new element
        .withMlWeight(200.0)
        .withPriority(2000)
        .withUsage(20)
        .withContributorId("ActionSearchEverywhereContributor")
        .build(),
      elementsFirstSearch[2],
      MockElementInfoBuilder() // new element
        .withMlWeight(400.0)
        .withPriority(4000)
        .withUsage(40)
        .withContributorId("ActionSearchEverywhereContributor")
        .build(),
    )

    elementsSecondSearch.zip(listOf(1, 2, 4, 3, 5)).forEach { (elementInfo, id) -> idProvider.setId(elementInfo.element, id) }

    val stateSecond = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsSecondSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateSecond, listOf(1, 2, 4, 3, 5), changedIds = listOf(2, 4, 5))
  }

  fun `test check only changed features are logged`() {
    val featuresCache = SearchEverywhereMlFeaturesCache()
    val idProvider = MockItemIdProvider()

    val elementsFirstSearch = listOf(
      MockElementInfoBuilder()
        .withMlWeight(100.0)
        .withPriority(1000)
        .withUsage(10)
        .withContributorId("FileSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(200.0)
        .withPriority(2000)
        .withUsage(20)
        .withContributorId("FileSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(300.0)
        .withPriority(3000)
        .withUsage(30)
        .withContributorId("FileSearchEverywhereContributor")
        .build()
    )

    elementsFirstSearch.forEach { idProvider.setId(it.element, null) }

    featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsFirstSearch,
      ::getContributorFeatures,
      idProvider
    )

    val elementsSecondSearch = listOf(
      MockElementInfoBuilder()
        .withMlWeight(100.0)
        .withPriority(1000)
        .withUsage(25) // 10 -> 25
        .withContributorId("FileSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(200.0)
        .withPriority(3000) // 2000 -> 3000
        .withContributorId("FileSearchEverywhereContributor")
        .withUsage(20)
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(400.0) // 300.0 -> 400.0
        .withContributorId("FileSearchEverywhereContributor")
        .withPriority(3000)
        .withUsage(30)
        .build()
    )

    elementsSecondSearch.forEachIndexed { index, it -> idProvider.setId(it.element, index + 1) }

    val stateSecond = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsSecondSearch,
      ::getContributorFeatures,
      idProvider
    )

    assertEventIds(stateSecond, listOf(1, 2, 3), listOf(1, 2, 3))

    stateSecond?.let {
      assertFeatures(
        it,
        listOf(listOf(), listOf(), listOf("mlWeight" to 400.0)),
        listOf(listOf("usage" to 25), listOf("priority" to 3000), listOf())
      )
    }
  }

  fun `test same contributors only logged once`() {
    val featuresCache = SearchEverywhereMlFeaturesCache()
    val idProvider = MockItemIdProvider()

    val elementsFirstSearch = listOf(
      MockElementInfoBuilder()
        .withMlWeight(100.0)
        .withPriority(1000)
        .withUsage(10)
        .withContributorId("FileSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(300.0)
        .withPriority(3000)
        .withUsage(30)
        .withContributorId("FileSearchEverywhereContributor")
        .build()
    )

    val contributorProvider = { element: SearchEverywhereFoundElementInfoWithMl ->
      listOf(
        CONTRIBUTOR_INFO_ID.with(element.contributor.searchProviderId),
        CONTRIBUTOR_WEIGHT.with(100)
      )
    }

    elementsFirstSearch.forEach { idProvider.setId(it.element, null) }

    featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsFirstSearch,
      contributorProvider,
      idProvider
    )

    val elementsSecondSearch = listOf(
      MockElementInfoBuilder()
        .withMlWeight(100.0)
        .withPriority(1000)
        .withUsage(10)
        .withContributorId("FileSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(300.0)
        .withPriority(3000)
        .withUsage(30)
        .withContributorId("FileSearchEverywhereContributor")
        .build()
    )

    elementsSecondSearch.forEachIndexed { index, it -> idProvider.setId(it.element, index + 1) }

    val stateSecond = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsSecondSearch,
      contributorProvider,
      idProvider
    )

    assertEventIds(stateSecond, listOf(1, 2), listOf())
  }

  fun `test different contributors logged twice`() {
    val featuresCache = SearchEverywhereMlFeaturesCache()
    val idProvider = MockItemIdProvider()

    val elementsFirstSearch = listOf(
      MockElementInfoBuilder()
        .withMlWeight(100.0)
        .withPriority(1000)
        .withUsage(10)
        .withContributorId("FileSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(300.0)
        .withPriority(3000)
        .withUsage(30)
        .withContributorId("ClassSearchEverywhereContributor")
        .build()
    )

    val firstContributorProvider = { element: SearchEverywhereFoundElementInfoWithMl ->
      listOf(
        CONTRIBUTOR_INFO_ID.with(element.contributor.searchProviderId),
        CONTRIBUTOR_WEIGHT.with(100)
      )
    }

    val secondContributorProvider = { element: SearchEverywhereFoundElementInfoWithMl ->
      if (element.contributor.searchProviderId == "FileSearchEverywhereContributor") {
        firstContributorProvider(element)
      }
      else {
        listOf(
          CONTRIBUTOR_INFO_ID.with(element.contributor.searchProviderId),
          CONTRIBUTOR_WEIGHT.with(200)
        )
      }
    }

    elementsFirstSearch.forEach { idProvider.setId(it.element, null) }

    featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsFirstSearch,
      firstContributorProvider,
      idProvider
    )

    val elementsSecondSearch = listOf(
      MockElementInfoBuilder()
        .withMlWeight(100.0)
        .withPriority(1000)
        .withUsage(10)
        .withContributorId("FileSearchEverywhereContributor")
        .build(),
      MockElementInfoBuilder()
        .withMlWeight(300.0)
        .withPriority(3000)
        .withUsage(30)
        .withContributorId("ClassSearchEverywhereContributor")
        .build()
    )

    elementsSecondSearch.forEachIndexed { index, it -> idProvider.setId(it.element, index + 1) }

    val stateSecond = featuresCache.getUpdateEventsAndCache(
      project,
      true,
      elementsSecondSearch,
      secondContributorProvider,
      idProvider
    )

    assertEventIds(stateSecond, listOf(1, 2), listOf(2))

    val updatedContributorEvents = hashMapOf(
      "contributorId" to "ClassSearchEverywhereContributor",
      "contributorWeight" to 200
    )

    stateSecond?.let {
      assertFeatures(
        it,
        listOf(listOf(), listOf("mlWeight" to 300.0, "contributor" to updatedContributorEvents)),
        listOf(listOf(), listOf("usage" to 30, "priority" to 3000))
      )
    }
  }
}

class SearchEverywhereMLElementCacheTest : BasePlatformTestCase() {
  fun `test update id is not replaced`() {
    val prevCacheNum = SearchEverywhereMLElementCache(id = 10)
    val prevCacheNull = SearchEverywhereMLElementCache(id = null)
    val newCache = SearchEverywhereMLElementCache(id = 20)

    TestCase.assertEquals(newCache.getDiff(prevCacheNull).id, 20)
    TestCase.assertEquals(newCache.getDiff(prevCacheNum).id, 20)
  }

  fun `test weight and actionId logged when changed`() {
    val prevCaches = listOf(
      SearchEverywhereMLElementCache(mlWeight = 100.0),
      SearchEverywhereMLElementCache(actionId = "100"),
      SearchEverywhereMLElementCache(),
      SearchEverywhereMLElementCache(mlWeight = 123.0, actionId = "123")
    )

    val newCaches = listOf(
      SearchEverywhereMLElementCache(actionId = "7"),
      SearchEverywhereMLElementCache(mlWeight = 7.0),
      SearchEverywhereMLElementCache(),
      SearchEverywhereMLElementCache(actionId = "23", mlWeight = 23.0)
    )

    newCaches.forEach { newCache ->
      prevCaches.forEach { oldCache ->
        TestCase.assertEquals(newCache.actionId, newCache.getDiff(oldCache).actionId)
        TestCase.assertEquals(newCache.mlWeight, newCache.getDiff(oldCache).mlWeight)
      }
    }
  }

  fun `test weight and actionId are not logged when are not changed`() {
    val prevCache = SearchEverywhereMLElementCache(mlWeight = 100.0, actionId = "1000")
    val prevCacheDiffWeight = SearchEverywhereMLElementCache(mlWeight = 800.0, actionId = "1000")
    val prevCacheDiffAction = SearchEverywhereMLElementCache(actionId = "8000", mlWeight = 100.0)
    val newCache = SearchEverywhereMLElementCache(mlWeight = 100.0, actionId = "1000")

    TestCase.assertNull(newCache.getDiff(prevCache).actionId)
    TestCase.assertNull(newCache.getDiff(prevCache).mlWeight)

    TestCase.assertNull(newCache.getDiff(prevCacheDiffAction).mlWeight)
    TestCase.assertEquals(newCache.actionId, newCache.getDiff(prevCacheDiffAction).actionId)

    TestCase.assertNull(newCache.getDiff(prevCacheDiffWeight).actionId)
    TestCase.assertEquals(newCache.mlWeight, newCache.getDiff(prevCacheDiffWeight).mlWeight)
  }

  fun `test if contributor changed, all fields logged`() {
    val prevCache = SearchEverywhereMLElementCache(
      id = 10,
      contributor = SearchEverywhereMlFeaturesCacheTest.getContributorFeatures("ActionSearchEverywhereContributor"),
      mlWeight = 100.0,
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(2),
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true)
      )
    )

    val newCache = SearchEverywhereMLElementCache(
      id = 10,
      contributor = SearchEverywhereMlFeaturesCacheTest.getContributorFeatures("FilesSearchEverywhereContributor"),
      mlWeight = 100.0,
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(2),
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true)
      )
    )

    val diffCache = newCache.getDiff(prevCache)

    TestCase.assertEquals(newCache.id, diffCache.id)
    TestCase.assertEquals(newCache.contributor, diffCache.contributor)
    TestCase.assertEquals(newCache.mlWeight, diffCache.mlWeight)
    TestCase.assertEquals(newCache.mlFeatures, diffCache.mlFeatures)
  }

  fun `test only ml features diff logged`() {
    val prevCache = SearchEverywhereMLElementCache(
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(2),
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true),
        SearchEverywhereActionFeaturesProvider.Fields.USAGE.with(5),
        SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(10011)
      )
    )

    val newCache = SearchEverywhereMLElementCache(
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(3), // 2 -> 3
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true),
        SearchEverywhereActionFeaturesProvider.Fields.USAGE.with(5),
        SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(10013), // 10011 -> 10013
        SearchEverywhereActionFeaturesProvider.Fields.IS_SEARCH_ACTION.with(true) // new
      )
    )

    val diffCache = newCache.getDiff(prevCache)

    UsefulTestCase.assertOrderedEquals(
      listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(3), // 2 -> 3
        SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(10013), // 10011 -> 10013
        SearchEverywhereActionFeaturesProvider.Fields.IS_SEARCH_ACTION.with(true) // new
      ),
      diffCache.mlFeatures?.toList() ?: emptyList()
    )
  }

  fun `test absent features logged`() {
    val prevCache = SearchEverywhereMLElementCache(
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(2),
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true),
        SearchEverywhereActionFeaturesProvider.Fields.USAGE.with(5),
        SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(10011),
        SearchEverywhereActionFeaturesProvider.Fields.IS_SEARCH_ACTION.with(true)
      )
    )

    val newCache = SearchEverywhereMLElementCache(
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(3), // 2 -> 3
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true),
        SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(10013), // 10011 -> 10013
      )
    )

    val diffCache = newCache.getDiff(prevCache)

    UsefulTestCase.assertOrderedEquals(
      listOf(
        SearchEverywhereActionFeaturesProvider.Fields.USAGE.name,
        SearchEverywhereActionFeaturesProvider.Fields.IS_SEARCH_ACTION.name
      ),
      diffCache.absentFeatures ?: emptyList()
    )
  }

  fun `test empty absent feature are not logged`() {
    val prevCache = SearchEverywhereMLElementCache(
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(2),
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true),
        SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(10011),
      )
    )
    val newCache = SearchEverywhereMLElementCache(
      mlFeatures = listOf(
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY.with(3), // 2 -> 3
        SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(true),
        SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY.with(10013), // 10011 -> 10013
      )
    )

    val diffCache = newCache.getDiff(prevCache)

    UsefulTestCase.assertNull(diffCache.absentFeatures)
  }

}