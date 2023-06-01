package com.intellij.searchEverywhereMl.typos.log

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.SearchEverywhereItemSelectedListener
import com.intellij.searchEverywhereMl.common.SE_TABS
import com.intellij.searchEverywhereMl.common.log.MLSE_RECORDER_ID

class SearchEverywhereTyposLogger : CounterUsagesCollector(), SearchEverywhereItemSelectedListener {
  companion object {
    private val GROUP = EventLogGroup("typos.log", 1, MLSE_RECORDER_ID)

    private val TAB_ID = EventFields.String("tabId", SE_TABS)

    private val SUGGESTION_PRESENT_FIELD = EventFields.Boolean("suggestionPresent")
    private val SUGGESTION_CLICKED_FIELD = EventFields.Boolean("suggestionClicked")
    private val SUGGESTION_CONFIDENCE_FIELD = EventFields.Float("suggestionConfidence")
    private val ITEM_SELECTED_EVENT = GROUP.registerVarargEvent("item.picked",
                                                                TAB_ID, SUGGESTION_PRESENT_FIELD,
                                                                SUGGESTION_CLICKED_FIELD, SUGGESTION_CONFIDENCE_FIELD)
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun onItemSelected(project: Project?,
                              tabId: String,
                              indexes: IntArray,
                              selectedItems: List<Any>,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                              closePopup: Boolean) {
    val suggestion = elementsProvider.invoke().first().element.takeIfIsInstance<SearchEverywhereSpellCheckResult.Correction>()

    val eventData = computeEventData(suggestion, selectedItems)
    ITEM_SELECTED_EVENT.log(eventData)
  }

  private fun computeEventData(suggestion: SearchEverywhereSpellCheckResult.Correction?,
                               selectedItems: List<Any>) = buildList {
    add(SUGGESTION_PRESENT_FIELD.with(suggestion != null))

    suggestion?.let {
      add(SUGGESTION_CLICKED_FIELD.with(selectedItems.contains(it)))
      add(SUGGESTION_CONFIDENCE_FIELD.with(it.confidence.toFloat()))
    }
  }

  private inline fun <reified T> Any.takeIfIsInstance(): T? = this.takeIf { it is T } as? T
}
