package com.intellij.stats.completion

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

    private val LOG_NOTHING = { items: List<LookupStringWithRelevance> -> Unit }
    
    private val itemsToId: MutableMap<String, Int> = hashMapOf()
    
    private var logLastAction = LOG_NOTHING
    private var lastCompletionList: List<LookupStringWithRelevance> = emptyList()

    override fun completionStarted(completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction = { items ->
            val builder = messageBuilder(Action.COMPLETION_STARTED)
            builder.nextEntity("COMP_LIST_LEN", items.size)
            builder.nextText(convertCompletionList(items))
            log(builder)
        }
    }

    override fun beforeCharTyped(c: Char, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction(completionList)
        
        logLastAction = { items ->
            val builder = messageBuilder(Action.TYPE)
            builder.nextEntity("COMP_LIST_LEN", items.size)
            builder.nextText(toIdsList(items))
            log(builder)
        }
    }

    override fun afterCharTyped(c: Char, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
    }

    override fun downPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
        
        val builder = messageBuilder(Action.DOWN)
        builder.nextEntity("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        log(builder)
    }

    override fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
        
        val builder = messageBuilder(Action.UP)
        builder.nextEntity("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        log(builder)
    }

    override fun completionCancelled() {
        logLastAction(lastCompletionList)
        logLastAction = LOG_NOTHING
        val builder = messageBuilder(Action.COMPLETION_CANCELED)
        log(builder)
    }

    override fun itemSelectedByTyping(itemName: String) {
        logLastAction(lastCompletionList)
        logLastAction = LOG_NOTHING
        val builder = messageBuilder(Action.TYPED_SELECT)
        builder.nextEntity("ID", itemsToId[itemName]!!)
        log(builder)
    }

    override fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
        
        val builder = messageBuilder(Action.EXPLICIT_SELECT)
        builder.nextEntity("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        log(builder)
    }

    override fun beforeBackspacePressed(completionList: List<LookupStringWithRelevance>) {
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
    }

    override fun afterBackspacePressed(pos: Int, itemName: String?, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        
        val builder = messageBuilder(Action.BACKSPACE)
        builder.nextEntity("POS", pos)
        builder.nextEntity("COMP_LIST_LEN", completionList.size)
        val id = getItemId(itemName, completionList)
        builder.nextEntity("ID", id)
        builder.nextText(toIdsList(completionList))
        log(builder)
    }

    private fun convertCompletionList(items: List<LookupStringWithRelevance>): String {
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

    private fun log(statInfoBuilder: StatInfoBuilder) {
        val msg = statInfoBuilder.message()
        println(msg)
        logFileManager.println(msg)
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

    private fun messageBuilder(action: Action): StatInfoBuilder {
        return StatInfoBuilder(installationUID, completionUID, action)
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
    TYPED_SELECT
}

class StatInfoBuilder(val installationUID: String, val completionUID: String, val action: Action) {
    private val timestamp = System.currentTimeMillis()
    private val builder = StringBuilder()

    init {
        builder.append("$installationUID $completionUID $timestamp $action")
    }

    fun nextText(any: Any) = builder.append(" $any")
    
    fun nextEntity(name: String, value: Any) = builder.append(" $name=$value")
    
    fun message() = builder.toString()

}

