// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.spellchecker.DictionaryLayer
import com.intellij.spellchecker.util.SpellCheckerBundle

object SpellcheckerActionStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("spellchecker.events", 4)

  private val DOMAIN_FIELD = EventFields.String("domain", listOf("code", "comment", "literal", "commit"))
  private val DICTIONARY_LAYER_FIELD = EventFields.String(
    "dictionary_layer",
    listOf(
      SpellCheckerBundle.message("dictionary.name.project.level"),
      SpellCheckerBundle.message("dictionary.name.application.level")
    )
  )
  private val SUGGESTION_INDEX_FIELD = EventFields.Int("suggestion_index")
  private val TOTAL_SUGGESTIONS_FIELD = EventFields.Int("total_suggestions")

  private val removeFromAcceptedWords: EventId = GROUP.registerEvent("remove.from.accepted.words.ui")
  private val addToAcceptedWords: EventId = GROUP.registerEvent("add.to.accepted.words.ui")

  private val suggestionShownEvent = GROUP.registerVarargEvent("suggestion.shown",
                                                               DOMAIN_FIELD,
                                                               EventFields.Language,
                                                               EventFields.PluginInfo)

  private val changeToInvokedEvent = GROUP.registerVarargEvent("change.to.invoked",
                                                               DOMAIN_FIELD,
                                                               SUGGESTION_INDEX_FIELD,
                                                               TOTAL_SUGGESTIONS_FIELD,
                                                               EventFields.Language,
                                                               EventFields.PluginInfo)

  private val renameToInvokedEvent = GROUP.registerVarargEvent("rename.to.invoked",
                                                               DOMAIN_FIELD,
                                                               TOTAL_SUGGESTIONS_FIELD,
                                                               EventFields.Language,
                                                               EventFields.PluginInfo)

  private val saveToInvokedEvent = GROUP.registerVarargEvent("save.to.invoked",
                                                             DOMAIN_FIELD,
                                                             DICTIONARY_LAYER_FIELD,
                                                             EventFields.Language,
                                                             EventFields.PluginInfo)


  @JvmStatic
  fun removeWordFromAcceptedWords(project: Project) {
    removeFromAcceptedWords.log(project)
  }

  @JvmStatic
  fun addWordToAcceptedWords(project: Project) {
    addToAcceptedWords.log(project)
  }

  @JvmStatic
  fun saveToPerformed(tracker: SpellcheckerRateTracker, dictionaryLayer: DictionaryLayer?) {
    val events = buildCommonEvents(tracker)
    dictionaryLayer?.let { events.add(DICTIONARY_LAYER_FIELD.with(it.name)) }
    saveToInvokedEvent.log(tracker.project, events)
  }

  @JvmStatic
  fun renameToPerformed(tracker: SpellcheckerRateTracker, total: Int) {
    val events = buildCommonEvents(tracker)
    events.add(TOTAL_SUGGESTIONS_FIELD.with(total))
    renameToInvokedEvent.log(tracker.project, buildCommonEvents(tracker))
  }

  @JvmStatic
  fun changeToPerformed(tracker: SpellcheckerRateTracker, index: Int, total: Int) {
    val events = buildCommonEvents(tracker)
    events.add(SUGGESTION_INDEX_FIELD.with(index))
    events.add(TOTAL_SUGGESTIONS_FIELD.with(total))
    changeToInvokedEvent.log(tracker.project, events)
  }

  @JvmStatic
  fun suggestionShown(tracker: SpellcheckerRateTracker) {
    suggestionShownEvent.log(tracker.project, buildCommonEvents(tracker))
  }

  private fun buildCommonEvents(tracker: SpellcheckerRateTracker): MutableList<EventPair<*>> {
    val events = mutableListOf<EventPair<*>>()
    events.add(DOMAIN_FIELD.with(tracker.domain))
    events.add(EventFields.Language.with(tracker.language))
    events.add(EventFields.PluginInfo.with(getPluginInfo(tracker.language.javaClass)))
    return events
  }
}
