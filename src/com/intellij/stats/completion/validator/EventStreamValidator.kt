package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.*
import java.io.*

data class EventLine(val event: LogEvent, val line: String)

class StreamValidator(input: InputStream,
                      output: OutputStream,
                      error: OutputStream) {
    
    private val inputReader = BufferedReader(InputStreamReader(input))
    private val outputWriter = BufferedWriter(OutputStreamWriter(output))
    private val errorWriter = BufferedWriter(OutputStreamWriter(error))

    var session = mutableListOf<EventLine>()
    var currentSessionUid = ""

    fun validate() {
        var line: String? = inputReader.readLine()
        
        while (line != null) {
            val event: LogEvent? = LogEventSerializer.fromString(line)
            if (event == null) {
                handleNullEvent(line)
                continue
            }

            if (currentSessionUid == event.sessionUid) {
                session.add(EventLine(event, line))
            }
            else {
                processCompletionSession(session)
                reset()
            }
        }
        
        processCompletionSession(session)
    }

    private fun processCompletionSession(session: List<EventLine>) {
        if (session.isEmpty()) return

        var isValidSession = false
        
        val initial = session.first()
        if (initial.event is CompletionStartedEvent) {
            val state = CompletionState(initial.event)
            session.drop(1).forEach { state.accept(it.event) }
            isValidSession = state.isFinished
        }
        
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
    var currentPosition    = event.currentPosition
    var completionList     = event.completionListIds
    val allCompletionItems = event.newCompletionListItems.toMutableList()

    var isValid = true
    var isFinished = false

    private fun updateState(nextEvent: LookupStateLogData) {
        currentPosition = nextEvent.currentPosition
        allCompletionItems.addAll(nextEvent.newCompletionListItems)
        if (nextEvent.completionListIds.isNotEmpty()) {
            completionList = nextEvent.completionListIds
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
        isValid = (beforeDownPressedPosition + 1) % completionList.size == currentPosition
    }

    override fun visit(event: UpPressedEvent) {
        val beforeUpPressedPosition = currentPosition
        updateState(event)
        
        isValid = (completionList.size + beforeUpPressedPosition - 1) % completionList.size == currentPosition
    }

    override fun visit(event: TypeEvent) {
        val listBefore = completionList 
        updateState(event)
        
        isValid = listBefore.containsAll(completionList)
    }

    override fun visit(event: BackspaceEvent) {
        val listBefore = completionList
        updateState(event)
        
        isValid = completionList.containsAll(listBefore)
    }

    override fun visit(event: ExplicitSelectEvent) {
        updateState(event)
        if (currentPosition >= completionList.size) {
            isValid = false
            return
        }
        
        val selectedId = completionList[currentPosition]
        isValid = completionList.find { it == selectedId } != null && allCompletionItems.find { it.id == selectedId } != null
        isFinished = true
    }

    override fun visit(event: CompletionCancelledEvent) {
        isFinished = true
    }

    override fun visit(event: TypedSelectEvent) {
        val id = event.selectedId
        
        isValid = allCompletionItems.find { it.id == id } != null && completionList.find { it == id } != null
        isFinished = true
    }
    
}



