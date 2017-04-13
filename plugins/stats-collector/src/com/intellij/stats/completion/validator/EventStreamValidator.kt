package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.*
import java.io.*

data class EventLine(val event: LogEvent, val line: String)

open class SessionsInputSeparator(input: InputStream,
                                  output: OutputStream,
                                  error: OutputStream) {
    
    private val inputReader = BufferedReader(InputStreamReader(input))
    private val outputWriter = BufferedWriter(OutputStreamWriter(output))
    private val errorWriter = BufferedWriter(OutputStreamWriter(error))

    var session = mutableListOf<EventLine>()
    var currentSessionUid = ""

    fun processInput() {
        var line: String? = inputReader.readLine()
        
        while (line != null) {
            val event: LogEvent? = LogEventSerializer.fromString(line)
            if (event == null) {
                handleNullEvent(line)
                continue
            }

            if (currentSessionUid != event.sessionUid) {
                processCompletionSession(session)
                reset()
                currentSessionUid = event.sessionUid
            }
            
            session.add(EventLine(event, line))
            line = inputReader.readLine()
        }
        
        processCompletionSession(session)
        
        outputWriter.flush()
        errorWriter.flush()
    }

    private fun processCompletionSession(session: List<EventLine>) {
        if (session.isEmpty()) return

        var isValidSession = false
        
        val initial = session.first()
        if (initial.event is CompletionStartedEvent) {
            val state = CompletionState(initial.event)
            session.drop(1).forEach { state.accept(it.event) }
            isValidSession = state.isFinished && state.isValid
        }

        onSessionProcessingFinished(session, isValidSession)
    }

    open protected fun onSessionProcessingFinished(session: List<EventLine>, isValidSession: Boolean) {
        val writer = if (isValidSession) outputWriter else errorWriter
        session.forEach {
            writer.write(it.line)
            writer.newLine()
        }
    }

    private fun handleNullEvent(line: String?) {
        processCompletionSession(session)
        reset()
        
        errorWriter.write(line)
        errorWriter.newLine()
    }

    private fun reset() {
        session.clear()
        currentSessionUid = ""
    }
    
}


class CompletionState(event: CompletionStartedEvent) : LogEventVisitor() {
    val allCompletionItemIds: MutableList<Int> = event.newCompletionListItems.map { it.id }.toMutableList()
    
    var currentPosition    = event.currentPosition
    var completionList     = event.completionListIds
    var currentId          = getSafeCurrentId(completionList, currentPosition)

    var isValid = true
    var isFinished = false

    private fun updateState(nextEvent: LookupStateLogData) {
        currentPosition = nextEvent.currentPosition
        allCompletionItemIds.addAll(nextEvent.newCompletionListItems.map { it.id })
        if (nextEvent.completionListIds.isNotEmpty()) {
            completionList = nextEvent.completionListIds
        }
        currentId = getSafeCurrentId(completionList, currentPosition)
    }

    private fun getSafeCurrentId(completionList: List<Int>, position: Int): Int {
        if (completionList.isEmpty()) {
            return -1
        }        
        else if (position < completionList.size && position >= 0) {
            return completionList[position]
        }
        else {
            isValid = false
            return -2
        }
    }

    fun accept(nextEvent: LogEvent) {
        if (isFinished) {
            isValid = false            
        }
        else if (isValid) {
            nextEvent.accept(this)
        }
    }
    
    override fun visit(event: DownPressedEvent) {
        val beforeDownPressedPosition = currentPosition
        updateState(event)
        updateValid((beforeDownPressedPosition + 1) % completionList.size == currentPosition)
    }

    private fun updateValid(value: Boolean) {
        isValid = isValid && value
    }

    override fun visit(event: UpPressedEvent) {
        val beforeUpPressedPosition = currentPosition
        updateState(event)
        
        updateValid((completionList.size + beforeUpPressedPosition - 1) % completionList.size == currentPosition)
    }

    override fun visit(event: TypeEvent) {
        val newIds = event.newCompletionIds()
        val allIds = (completionList + newIds).toSet()
        
        updateValid(allIds.containsAll(event.completionListIds))
        
        updateState(event)
        updateValid(allCompletionItemIds.containsAll(completionList))
    }

    override fun visit(event: BackspaceEvent) {
        updateState(event)
        updateValid(allCompletionItemIds.containsAll(completionList))
    }

    override fun visit(event: ExplicitSelectEvent) {
        val selectedIdBefore = currentId
        updateState(event)

        updateValid(selectedIdBefore == currentId && allCompletionItemIds.find { it == currentId } != null)
        isFinished = true
    }

    override fun visit(event: CompletionCancelledEvent) {
        isFinished = true
    }

    override fun visit(event: TypedSelectEvent) {
        val id = event.selectedId
        updateValid(completionList[0] == id)
        isFinished = true
    }
    
}


//fun main(args: Array<String>) {
//    val inputStream = FileInputStream("bad_one.txt")
    
//    val good = FileOutputStream("good.txt")
//    val bad = FileOutputStream("bad.txt")
    
//    val good = System.out
//    val bad = System.err
    
//    val separator = SessionsInputSeparator(inputStream, good, bad)
//    separator.processInput()
//}