package com.intellij.stats.completion.events

import com.google.gson.Gson
import com.intellij.stats.completion.Action
import java.util.*

object JsonSerializer {
    private val gson = Gson()
    fun toJson(obj: Any) = gson.toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>) = gson.fromJson(json, clazz)
}

abstract class LogEvent(var userUid: String, type: Action) {
    @Transient var recorderId = "completion-stats"
    @Transient var timestamp = System.currentTimeMillis()
    @Transient var sessionUid: String = UUID.randomUUID().toString()
    @Transient var actionType: Action = type
    
    abstract fun accept(visitor: LogEventVisitor)
        
}
    
object LogEventSerializer {

    val actionClassMap: Map<Action, Class<out LogEvent>> = mapOf(
            Pair(Action.COMPLETION_STARTED, CompletionStartedEvent::class.java),
            Pair(Action.TYPE, TypeEvent::class.java),
            Pair(Action.DOWN, DownPressedEvent::class.java),
            Pair(Action.UP, UpPressedEvent::class.java),
            Pair(Action.BACKSPACE, BackspaceEvent::class.java),
            Pair(Action.COMPLETION_CANCELED, CompletionCancelledEvent::class.java),
            Pair(Action.EXPLICIT_SELECT, ExplicitSelectEvent::class.java),
            Pair(Action.TYPED_SELECT, ItemSelectedByTypingEvent::class.java)
    )

    fun toString(event: LogEvent): String {
        return "${event.timestamp}\t${event.recorderId}\t${event.userUid}\t${event.sessionUid}\t${event.actionType}\t${JsonSerializer.toJson(this)}"
    }

    fun fromString(line: String): LogEvent? {
        val items = mutableListOf<String>()

        var start = -1
        for (i in 0..4) {
            val nextSpace = line.indexOf('\t', start + 1)
            val newItem = line.substring(start + 1, nextSpace)
            items.add(newItem)
            start = nextSpace
        }

        val timestamp = items[0].toLong()
        val recorderId = items[1]
        val userUid = items[2]
        val sessionUid = items[3]
        val actionType = Action.valueOf(items[4])

        val clazz = actionClassMap[actionType] ?: return null

        val json = line.substring(start + 1)
        val obj = JsonSerializer.fromJson(json, clazz)

        obj.userUid = userUid
        obj.timestamp = timestamp
        obj.recorderId = recorderId
        obj.sessionUid = sessionUid
        obj.actionType = actionType

        return obj
    }

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
        selectedPosition: Int) : LookupStateLogData(userId, Action.UP, completionListIds, newCompletionListItems, selectedPosition) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class DownPressedEvent(
        userId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, Action.DOWN, completionListIds, newCompletionListItems, selectedPosition) {

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

class CompletionCancelledEvent(userId: String) : LogEvent(userId, Action.COMPLETION_CANCELED) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

class ItemSelectedByTypingEvent(userId: String, var selectedId: Int) : LogEvent(userId, Action.TYPED_SELECT) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

class ExplicitSelectEvent(userId: String, 
                          completionListIds: List<Int>,
                          newCompletionListItems: List<LookupEntryInfo>,
                          selectedPosition: Int) : LookupStateLogData(userId, Action.EXPLICIT_SELECT, completionListIds, newCompletionListItems, selectedPosition) {
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
    
}

class BackspaceEvent(
        userId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>, 
        selectedPosition: Int) : LookupStateLogData(userId, Action.BACKSPACE, completionListIds, newCompletionListItems, selectedPosition) {

    init {
        assert(completionListIds.isNotEmpty())
    }
    
    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class TypeEvent(
        userId: String,
        completionListIds: List<Int>,
        newCompletionListItems: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, Action.TYPE, completionListIds, newCompletionListItems, selectedPosition) {

    init {
        assert(completionListIds.isNotEmpty())
    }

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class CompletionStartedEvent(
        userId: String,
        var performExperiment: Boolean,
        var experimentVersion: Int,
        completionList: List<LookupEntryInfo>,
        selectedPosition: Int) : LookupStateLogData(userId, Action.COMPLETION_STARTED, completionList.map { it.id }, completionList, selectedPosition) 

{
    var completionListLength: Int = completionList.size

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}

class LookupEntryInfo(val id: Int, val length: Int, val relevance: Map<String, Any>)


abstract class LogEventVisitor {

    open fun visit(event: CompletionStartedEvent) {
    }

    open fun visit(event: TypeEvent) {
    }

    open fun visit(event: DownPressedEvent) {
    }

    open fun visit(event: UpPressedEvent) {
    }

    open fun visit(event: BackspaceEvent) {
    }
    
    open fun visit(event: CompletionCancelledEvent) {
    }

    open fun visit(event: ExplicitSelectEvent) {
    }
    
    open fun visit(event: ItemSelectedByTypingEvent) {
    }
    
}    
