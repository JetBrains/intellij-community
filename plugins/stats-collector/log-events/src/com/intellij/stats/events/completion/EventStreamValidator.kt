/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.stats.events.completion

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
            
            session.add(com.intellij.stats.events.completion.EventLine(event, line))
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
            val state = com.intellij.stats.events.completion.CompletionValidationState(initial.event)
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


class CompletionValidationState(event: CompletionStartedEvent) : LogEventVisitor() {
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