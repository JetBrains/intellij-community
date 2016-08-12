package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.*
import java.io.*

data class EventLine(val event: LogEvent, val line: String)

class SessionsInputSeparator(input: InputStream,
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
    val allCompletionItems = event.newCompletionListItems.toMutableList()
    
    var currentPosition    = event.currentPosition
    var completionList     = event.completionListIds
    var currentId          = getSafeCurrentId(completionList, currentPosition)

    var isValid = true
    var isFinished = false

    private fun updateState(nextEvent: LookupStateLogData) {
        currentPosition = nextEvent.currentPosition
        allCompletionItems.addAll(nextEvent.newCompletionListItems)
        if (nextEvent.completionListIds.isNotEmpty()) {
            completionList = nextEvent.completionListIds
        }
        currentId = getSafeCurrentId(completionList, currentPosition)
    }

    private fun getSafeCurrentId(completionList: List<Int>, position: Int): Int {
        if (completionList.isEmpty()) {
            return -1
        }        
        else if (position < completionList.size && position > 0) {
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
        val selectedIdBefore = currentId
        updateState(event)

        isValid = selectedIdBefore == currentId && allCompletionItems.find { it.id == currentId } != null
        isFinished = true
    }

    override fun visit(event: CompletionCancelledEvent) {
        isFinished = true
    }

    override fun visit(event: TypedSelectEvent) {
        val id = event.selectedId
        isValid = completionList.size == 1 && completionList[0] == id
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