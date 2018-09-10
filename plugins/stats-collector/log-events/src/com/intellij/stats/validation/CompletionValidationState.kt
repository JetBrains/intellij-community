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

package com.intellij.stats.validation

import com.intellij.stats.completion.LogEventVisitor
import com.intellij.stats.completion.events.*
import org.jetbrains.annotations.TestOnly

class CompletionValidationState(event: CompletionStartedEvent) : LogEventVisitor() {
    val allCompletionItemIds: MutableList<Int> = event.newCompletionListItems.map { it.id }.toMutableList()

    var currentPosition: Int = event.currentPosition
    var completionList: List<Int> = event.completionListIds
    var currentId: Int = getSafeCurrentId(completionList, currentPosition)

    private var isValid = true
    private var isFinished = false
    private var errorMessage = ""

    private var events = mutableListOf<LogEvent>(event)

    private fun updateState(nextEvent: LookupStateLogData) {
        currentPosition = nextEvent.currentPosition
        allCompletionItemIds.addAll(nextEvent.newCompletionListItems.map { it.id })
        if (nextEvent.completionListIds.isNotEmpty()) {
            completionList = nextEvent.completionListIds
        }
        currentId = getSafeCurrentId(completionList, currentPosition)
    }

    private fun getSafeCurrentId(completionList: List<Int>, position: Int): Int {
        return if (completionList.isEmpty()) {
            -1
        }
        else if (position < completionList.size && position >= 0) {
            completionList[position]
        }
        else {
            if (errorMessage.isEmpty()) {
                errorMessage = "completion list size: ${completionList.size}, requested position: $position"
            }
            isValid = false
            -2
        }
    }

    fun accept(nextEvent: LogEvent) {
        events.add(nextEvent)

        if (isFinished) {
            errorMessage = "activity after completion finish session"
            isValid = false
        }
        else if (isValid) {
            nextEvent.accept(this)
        }
    }

    override fun visit(event: DownPressedEvent) {
        val beforeDownPressedPosition = currentPosition
        updateState(event)
        val isCorrectPosition = (beforeDownPressedPosition + 1) % completionList.size == currentPosition
        updateValid(isCorrectPosition,
                "position after up pressed event incorrect, before event $beforeDownPressedPosition, " +
                        "now $currentPosition, " +
                        "elements in list ${completionList.size}"
        )
    }

    private fun updateValid(value: Boolean, error: String) {
        val wasValidBefore = isValid
        isValid = isValid && value
        if (wasValidBefore && !isValid) {
            errorMessage = error
        }
    }

    override fun visit(event: UpPressedEvent) {
        val beforeUpPressedPosition = currentPosition
        updateState(event)

        val isCorrectPosition = (completionList.size + beforeUpPressedPosition - 1) % completionList.size == currentPosition
        updateValid(isCorrectPosition,
                "position after up pressed event incorrect, before event $beforeUpPressedPosition, " +
                "now $currentPosition, " +
                "elements in list ${completionList.size}"
        )
    }

    override fun visit(event: TypeEvent) {
        updateState(event)
        updateValid(allCompletionItemIds.containsAll(completionList),
                "TypeEvent: all elements in completion are registered")
    }

    override fun visit(event: BackspaceEvent) {
        updateState(event)
        updateValid(allCompletionItemIds.containsAll(completionList),
                "Backspace: some elements in completion list are not registered")
    }

    override fun visit(event: ExplicitSelectEvent) {
        val selectedIdBefore = currentId
        updateState(event)

        updateValid(selectedIdBefore == currentId && allCompletionItemIds.find { it == currentId } != null,
                "Selected element was not registered")

        isFinished = true
    }

    override fun visit(event: CompletionCancelledEvent) {
        isFinished = true
    }

    override fun visit(event: TypedSelectEvent) {
        val id = event.selectedId
        updateValid(completionList[currentPosition] == id,
                "Element selected by typing is not the same id")

        isFinished = true
    }


    fun isSessionValid(): Boolean {
        return isValid && isFinished
    }

    @TestOnly
    fun isCurrentlyValid(): Boolean {
        return isValid
    }

    fun errorMessage(): String {
        return if (isValid && !isFinished) {
            "Session was not finished"
        }
        else {
            errorMessage
        }
    }

}