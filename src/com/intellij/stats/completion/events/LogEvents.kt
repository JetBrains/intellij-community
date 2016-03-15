package com.intellij.stats.completion.events

import com.google.gson.Gson
import com.intellij.stats.completion.Action
import java.util.*

object Serializer {
    private val gson = Gson()
    fun toJson(obj: Any) = gson.toJson(obj)
}

abstract class LogEvent(val userUid: String, val type: Action) {
    @Transient val recorderId = 0
    @Transient val timestamp = System.currentTimeMillis()
    @Transient val sessionUid: String = UUID.randomUUID().toString()
    @Transient val actionType: Action = type

    open fun serializeEventData() = Serializer.toJson(this)

    fun toLogLine(): String = "$timestamp $recorderId $userUid $sessionUid $actionType ${serializeEventData()}"
}


abstract class LookupStateLogData(userId: String,
                                  action: Action,
                                  var completionListIds: List<Int>,
                                  var newCompletionListItems: List<LookupEntryInfo>,
                                  var currentPosition: Int): LogEvent(userId, action)

class UpPressedEvent(
        userId: String, 
        completionListIds: List<Int>, 
        newCompletionListItems: List<LookupEntryInfo>, 
        selectedPosition: Int) : LookupStateLogData(userId, Action.UP, completionListIds, newCompletionListItems, selectedPosition)

class DownPressedEvent(
        userId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, Action.DOWN, completionListIds, newCompletionListItems, selectedPosition)

class CompletionCancelledEvent(userId: String) : LogEvent(userId, Action.COMPLETION_CANCELED)

class ItemSelectedByTypingEvent(userId: String, var selectedId: Int) : LogEvent(userId, Action.TYPED_SELECT)

class ExplicitSelectEvent(userId: String, 
                          completionListIds: List<Int>,
                          newCompletionListItems: List<LookupEntryInfo>,
                          selectedPosition: Int) : LookupStateLogData(userId, Action.EXPLICIT_SELECT, completionListIds, newCompletionListItems, selectedPosition)

class BackspaceEvent(
        userId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>, 
        selectedPosition: Int) : LookupStateLogData(userId, Action.BACKSPACE, completionListIds, newCompletionListItems, selectedPosition)

class TypeEvent(
        userId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, Action.TYPE, completionListIds, newCompletionListItems, selectedPosition)

class CompletionStartedEvent(
        userId: String,
        var performExperiment: Boolean,
        var experimentVersion: Int,
        completionList: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, Action.COMPLETION_STARTED, completionList.map { it.id }, completionList, selectedPosition) 

{
    var completionListLength: Int = completionList.size
}

class LookupEntryInfo(val id: Int, val length: Int, val relevance: Map<String, Any>)