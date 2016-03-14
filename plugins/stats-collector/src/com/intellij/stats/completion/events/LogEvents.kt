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

class BackspaceEvent(
        userId: String,
        var completionListIds: List<Int>,
        var newCompletionListItems: List<LookupEntryInfo>) : LogEvent(userId, Action.BACKSPACE)

class TypeEvent(
        userId: String,
        var completionListIds: List<Int>,
        var newCompletionListItems: List<LookupEntryInfo>) : LogEvent(userId, Action.TYPE)

class CompletionStartedEvent(
        userId: String,
        var performExperiment: Boolean,
        var experimentVersion: Int,
        var completionList: List<LookupEntryInfo>) : LogEvent(userId, Action.COMPLETION_STARTED) 
{
    var completionListLength: Int = completionList.size
}

class LookupEntryInfo(val id: Int, val length: Int, val relevance: Map<String, Any>)