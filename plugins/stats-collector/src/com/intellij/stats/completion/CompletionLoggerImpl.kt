package com.intellij.stats.completion

import com.google.gson.Gson
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.util.*

class CompletionFileLoggerProvider(private val logFileManager: LogFileManager) : CompletionLoggerProvider() {
    override fun dispose() {
        logFileManager.dispose()
    }

    private fun String.shortedUUID(): String {
        val start = this.lastIndexOf('-')
        if (start > 0 && start + 1 < this.length) {
            return this.substring(start + 1)
        }
        return this
    }

    override fun newCompletionLogger(): CompletionLogger {
        val installationUID = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
        val completionUID = UUID.randomUUID().toString()
        return CompletionFileLogger(installationUID.shortedUUID(), completionUID.shortedUUID(), logFileManager)
    }
}


class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val logFileManager: LogFileManager) : CompletionLogger() {

    private val itemsToId: MutableMap<String, Int> = hashMapOf()

    override fun completionStarted(completionList: List<LookupStringWithRelevance>,
                                   isExperimentPerformed: Boolean,
                                   experimentVersion: Int) 
    {
        val builder = logBuilder(Action.COMPLETION_STARTED)
        builder.addPair("EXPERIMENT_PERFORMED", isExperimentPerformed)
        builder.addPair("EXPERIMENT_VERSION", experimentVersion)
        builder.addPair("COMP_LIST_LEN", completionList.size)
        builder.addText(firstCompletionListText(completionList))
        log(builder)
    }

    override fun customMessage(message: String) {
        val builder = logBuilder(Action.CUSTOM)
        builder.addText(message)
        log(builder)
    }

    override fun afterCharTyped(c: Char, completionList: List<LookupStringWithRelevance>) {
        val builder = logBuilder(Action.TYPE)
        builder.addPair("COMP_LIST_LEN", completionList.size)
        builder.addText(createIdListAddUntrackedItems(completionList))
        log(builder)
    }

    override fun downPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = logBuilder(Action.DOWN)
        builder.addPair("POS", pos)

        val id = getItemId(itemName)
        var idsList = ""
        if (id == null) {
            idsList = createIdListAddUntrackedItems(completionList)
        }

        builder.addPair("ID", getItemId(itemName)!!)

        if (idsList.isNotEmpty()) {
            builder.addText(idsList)
        }
        
        log(builder)
    }

    override fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = logBuilder(Action.UP)
        builder.addPair("POS", pos)

        val id = getItemId(itemName)
        var idsList = ""
        if (id == null) {
            idsList = createIdListAddUntrackedItems(completionList)
        }

        builder.addPair("ID", getItemId(itemName)!!)

        if (idsList.isNotEmpty()) {
            builder.addText(idsList)
        }
        
        builder.addPair("ID", getItemId(itemName)!!)
        log(builder)
    }

    override fun completionCancelled() {
        val builder = logBuilder(Action.COMPLETION_CANCELED)
        log(builder)
    }

    override fun itemSelectedByTyping(itemName: String) {
        val builder = logBuilder(Action.TYPED_SELECT)
        
        //todo remove: for debugging purposes
        if (getItemId(itemName) == null) {
            builder.addPair("ID", "-777")
            builder.addPair("ITEM", itemName)
            
            val allValues = itemsToId.map {
                "${it.key}=${it.value}"
            }.joinToString(",", "[", "]")
            
            builder.addText(allValues)
            log(builder)
            return
        }

        val id = getItemId(itemName)!!
        builder.addPair("ID", id)
        log(builder)
    }

    override fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = logBuilder(Action.EXPLICIT_SELECT)
        builder.addPair("POS", pos)
        
        var id = getItemId(itemName)
        var ids = ""
        if (id == null) {
            ids = createIdListAddUntrackedItems(completionList)
            id = getItemId(itemName)
        }
        
        builder.addPair("ID", id!!)

        if (ids.isNotEmpty()) {
            builder.addText(ids)
        }
        
        log(builder)
    }

    override fun afterBackspacePressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = logBuilder(Action.BACKSPACE)
        builder.addPair("POS", pos)
        builder.addPair("COMP_LIST_LEN", completionList.size)
        val ids = createIdListAddUntrackedItems(completionList)
        val id = getItemId(itemName)
        if (id != null) {
            builder.addPair("ID", id)
        } else {
            builder.addPair("ID", "NULL($itemName)")
        }
        builder.addText(ids)
        log(builder)
    }

    private fun firstCompletionListText(items: List<LookupStringWithRelevance>): String {
        val builder = StringBuilder()
        with(builder, {
            append("[")
            var first = true
            items.forEach {
                if (!first) {
                    append(", ")
                }
                first = false
                val id = addNewItem(it.item)
                append("{ID=$id, ${it.toData()}}")
            }
            append("]")
        })
        return builder.toString()
    }
    
    private fun log(logLineBuilder: LogLineBuilder) {
        val msg = logLineBuilder.text()
        logFileManager.println(msg)
    }

    private fun getItemId(itemName: String): Int? {
        return itemsToId[itemName]
    }
    
    private fun addNewItem(itemName: String): Int {
        val size = itemsToId.size
        itemsToId[itemName] = size
        return size 
    }

    private fun createIdListAddUntrackedItems(items: List<LookupStringWithRelevance>): String {
        val idsList = StringBuilder()
        with (idsList) {
            append("[")
            var first = true
            items.forEach {
                if (!first) {
                    append(", ")
                }
                first = false
                
                var relevance = ""
                if (getItemId(it.item) == null) {
                    addNewItem(it.item)
                    relevance = " ${it.toData()}"
                }
                append("ID=${getItemId(it.item)!!}$relevance")
            }
            append(']')
        }
        return idsList.toString()
    }

    private fun logBuilder(action: Action): LogLineBuilder {
        return LogLineBuilder(installationUID, completionUID, action)
    }

}

enum class Action {
    COMPLETION_STARTED,
    TYPE,
    BACKSPACE,
    UP,
    DOWN,
    COMPLETION_CANCELED,
    EXPLICIT_SELECT,
    TYPED_SELECT,
    CUSTOM
}

class LogLineBuilder(val installationUID: String, val completionUID: String, val action: Action) {
    private val timestamp = System.currentTimeMillis()
    private val builder = StringBuilder()

    init {
        builder.append("$installationUID $completionUID $timestamp $action")
    }

    fun addText(any: Any) = builder.append(" $any")
    
    fun addPair(name: String, value: Any) = builder.append(" $name=$value")
    
    fun text() = builder.toString()

}

object Serializer {
    private val gson = Gson()
    fun toJson(obj: Any) = gson.toJson(obj)
}

abstract class LogEvent(val userUid: String, val type: Action) {
    @Transient val recorderId = 0
    @Transient val timestamp = System.currentTimeMillis()
    @Transient val sessionUid: String = UUID.randomUUID().toString()
    @Transient val actionType: Action = type
    
    abstract fun serializeEventData(): String
    
    fun toLogLine(): String = "$timestamp $recorderId $userUid $sessionUid $actionType ${serializeEventData()}"
}


class CompletionStartedEvent(userId: String,
                             performExperiment: Boolean, 
                             experimentVersion: Int, 
                             list: List<LookupEntryInfo>): LogEvent(userId, Action.COMPLETION_STARTED) {

    var experimentVersion: Int = experimentVersion
    var performExperiment: Boolean = performExperiment
    var completionListLength: Int = list.size
    var completionList: List<LookupEntryInfo> = list

    override fun serializeEventData(): String = Serializer.toJson(this)
}

class LookupEntryInfo(val id: Int, val length: Int, val relevance: Map<String, Any>)