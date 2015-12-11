package com.intellij.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*


abstract class CompletionLoggerProvider {
    
    abstract fun newCompletionLogger(): CompletionLogger
    
    open fun dispose() = Unit
    
    companion object Factory {
        fun getInstance(): CompletionLoggerProvider = ServiceManager.getService(CompletionLoggerProvider::class.java)
    }

}

class CompletionFileLoggerProvider(val filePathProvider: FilePathProvider) : CompletionLoggerProvider() {
    var initialized = false
    val writer: PrintWriter by lazy {
        val path = filePathProvider.statsFilePath
        val file = File(path)
        if (!file.exists()) {
            file.createNewFile()
        }
        val bufferedWriter = Files.newBufferedWriter(file.toPath(), StandardOpenOption.APPEND)
        initialized = true
        PrintWriter(bufferedWriter)
    }
    
    override fun dispose() {
        if (initialized) {
            writer.close()
        }
    }
    
    override fun newCompletionLogger(): CompletionLogger {
        val installationUID = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
        val completionUID = UUID.randomUUID().toString()
        return CompletionFileLogger(installationUID, completionUID, writer)  
    }

}


abstract class CompletionLogger {
    
    abstract fun completionStarted(items: List<LookupStringWithRelevance>)

    abstract fun downPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun backspacePressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)
    
    abstract fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>)

    abstract fun charTyped(c: Char, completionList: List<LookupStringWithRelevance>)
    
    abstract fun completionCancelled()
    
    abstract fun itemSelectedByTyping(itemName: String)
    
}

class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val writer: PrintWriter) : CompletionLogger() {
    
    private fun log(statInfoBuilder: StatInfoBuilder) {
        val msg = statInfoBuilder.message()
        println(msg)
        writer.println(msg)
    }
    
    private val itemsToId: MutableMap<String, Int> = hashMapOf()
    
    override fun completionStarted(items: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.COMPLETION_STARTED)
        builder.nextWrappedToken("COMP_LIST_LEN", items.size)
        builder.nextToken(convertCompletionList(items))
        log(builder)
    }
    
    fun convertCompletionList(items: List<LookupStringWithRelevance>): String {
        addUntrackedItemsToIdMap(items)
        
        val builder = StringBuilder()
        with(builder, {
            append("{")
            items.forEach {
                append("ID(")
                append(itemsToId[it.item])
                append(") ")
                append(it.toData())
                append(", ")
            }
            append("}")
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
        builder.nextWrappedToken("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextWrappedToken("ID", id)
        log(builder)
    }

    override fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.UP)
        builder.nextWrappedToken("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextWrappedToken("ID", id)
        log(builder)
    }

    override fun backspacePressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.BACKSPACE)
        builder.nextWrappedToken("POS", pos)
        builder.nextWrappedToken("COMP_LIST_LEN", completionList.size)
        val id = getItemId(itemName, completionList)
        builder.nextWrappedToken("ID", id)
        builder.nextToken(toIdsList(completionList))
        log(builder)
    }

    private fun getItemId(itemName: String, newCompletionList: List<LookupStringWithRelevance>): Int {
        if (itemsToId[itemName] == null) {
            addUntrackedItemsToIdMap(newCompletionList)
        }
        return itemsToId[itemName]!!
    }

    override fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.EXPLICIT_SELECT)
        builder.nextWrappedToken("POS", pos)
        val id = getItemId(itemName, completionList)
        builder.nextWrappedToken("ID", id)
        log(builder)
    }

    override fun charTyped(c: Char, completionList: List<LookupStringWithRelevance>) {
        val builder = messageBuilder(Action.TYPE)
        builder.nextWrappedToken("COMP_LIST_LEN", completionList.size)
        builder.nextToken(toIdsList(completionList))
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
        builder.nextWrappedToken("ID", itemsToId[itemName]!!)
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
        with (builder) {
            append(installationUID)
            append(' ')
            append(completionUID)
            append(' ')
            append(timestamp)
            append(' ')
            append(action)
        }
    }

    fun nextToken(any: Any) {
        builder.append(' ').append(any)
    }
    
    fun nextWrappedToken(info: String, any: Any) {
        builder.append(' ')
                .append(info)
                .append('(').append(any).append(')')
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