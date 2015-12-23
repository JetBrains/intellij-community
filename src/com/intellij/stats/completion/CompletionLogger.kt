package com.intellij.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.util.*


abstract class CompletionLoggerProvider {
    
    abstract fun newCompletionLogger(): CompletionLogger
    
    open fun dispose() = Unit
    
    companion object Factory {
        fun getInstance(): CompletionLoggerProvider = ServiceManager.getService(CompletionLoggerProvider::class.java)
    }

}

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


abstract class CompletionLogger {
    
    abstract fun completionStarted(items: List<LookupStringWithRelevance>)

    abstract fun downPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun backspacePressed(pos: Int, itemName: String?, completionList: List<LookupStringWithRelevance>)
    
    abstract fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun charTyped(c: Char, completionList: List<LookupStringWithRelevance>)
    
    abstract fun completionCancelled()
    
    abstract fun itemSelectedByTyping(itemName: String)
    
}

class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val logFileManager: LogFileManager) : CompletionLogger() {
    
    private fun log(statInfoBuilder: StatInfoBuilder) {
        val msg = statInfoBuilder.message()
        println(msg)
        logFileManager.println(msg)
    }
    
    private val itemsToId: MutableMap<String, Int> = hashMapOf()
    
    override fun completionStarted(items: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.COMPLETION_STARTED)
        builder.nextEntity("COMP_LIST_LEN", items.size)
        builder.nextText(convertCompletionList(items))
        log(builder)
    }
    
    fun convertCompletionList(items: List<LookupStringWithRelevance>): String {
        addUntrackedItemsToIdMap(items)
        val builder = StringBuilder()
        with(builder, {
            append("[")
            var first = true
            items.forEach {
                if (!first) {
                    append(", ")
                }
                first = false
                val itemName = itemsToId[it.item]
                append("{ID=$itemName ${it.toData()}}")
            }
            append("]")
        })
        return builder.toString()
    }

    private fun addUntrackedItemsToIdMap(items: List<LookupStringWithRelevance>) {
        items.forEach {
            val lookupString = it.item
            var id = itemsToId[lookupString]
            if (id == null) {
                itemsToId[lookupString] = itemsToId.size
            }
        }
    }

    override fun downPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.DOWN)
        builder.nextEntity("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        log(builder)
    }

    override fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.UP)
        builder.nextEntity("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        log(builder)
    }

    override fun backspacePressed(pos: Int, itemName: String?, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.BACKSPACE)
        builder.nextEntity("POS", pos)
        builder.nextEntity("COMP_LIST_LEN", completionList.size)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        builder.nextText(toIdsList(completionList))
        log(builder)
    }

    private fun getItemId(itemName: String?, newCompletionList: List<LookupStringWithRelevance>): Int {
        if (itemName == null) {
            //maybe completion list was not rebuild
            return -777
        }
        
        if (itemsToId[itemName] == null) {
            addUntrackedItemsToIdMap(newCompletionList)
        }
        return itemsToId[itemName]!!
    }

    override fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.EXPLICIT_SELECT)
        builder.nextEntity("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        log(builder)
    }

    override fun charTyped(c: Char, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.TYPE)
        builder.nextEntity("COMP_LIST_LEN", completionList.size)
        builder.nextText(toIdsList(completionList))
        log(builder)
    }
    
    private fun toIdsList(items: List<LookupStringWithRelevance>): String {
        addUntrackedItemsToIdMap(items)
        val ids = StringBuilder()
        with (ids) {
            append("{")
            items.forEach { 
                append("ID(")
                append(itemsToId[it.item])
                append("), ")
            }
            append('}')
        }
        return ids.toString()
    }

    override fun completionCancelled() {
        val builder = messageBuilder(Action.COMPLETION_CANCELED)
        log(builder)
    }

    override fun itemSelectedByTyping(itemName: String) {
        val builder = messageBuilder(Action.TYPED_SELECT)
        builder.nextEntity("ID", itemsToId[itemName]!!)
        log(builder)
    }
    
    private fun messageBuilder(action: Action): StatInfoBuilder {
        return StatInfoBuilder(installationUID, completionUID, action)
    }
    
}



class StatInfoBuilder(val installationUID: String, val completionUID: String, val action: Action) {
    private val timestamp = System.currentTimeMillis()
    private val builder = StringBuilder()
    
    init {
        builder.append("$installationUID $completionUID $timestamp $action")
    }

    fun nextText(any: Any) {
        builder.append(" $any")
    }
    
    fun nextEntity(name: String, value: Any) {
        builder.append(" $name=$value")
    }

    
    fun message() = builder.toString()

}


enum class Action {
    COMPLETION_STARTED,
    TYPE,
    BACKSPACE,
    UP,
    DOWN,
    COMPLETION_CANCELED,
    EXPLICIT_SELECT,
    TYPED_SELECT
}