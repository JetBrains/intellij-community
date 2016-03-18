package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.*

class CompletionState(event: CompletionStartedEvent) {
    var currentPosition    = event.currentPosition
    var completionList     = event.completionListIds
    val allCompletionItems = event.newCompletionListItems.toMutableList()

    var isFinished = false
    var isValid    = true

    private fun updateState(nextEvent: LookupStateLogData) {
        currentPosition = nextEvent.currentPosition
        allCompletionItems.addAll(nextEvent.newCompletionListItems)
        if (nextEvent.completionListIds.isNotEmpty()) {
            completionList = nextEvent.completionListIds
        }
    }
    
    fun feed(event: DownPressedEvent) {
        val beforeDownPressedPosition = currentPosition
        updateState(event)
        isValid = (beforeDownPressedPosition + 1) % completionList.size == currentPosition
    }

    fun feed(event: UpPressedEvent) {
        val beforeUpPressedPosition = currentPosition
        updateState(event)
        
        isValid = (completionList.size + beforeUpPressedPosition - 1) % completionList.size == currentPosition
    }

    fun feed(event: TypeEvent) {
        val listBefore = completionList 
        updateState(event)
        
        isValid = listBefore.containsAll(completionList)
    }

    fun feed(event: BackspaceEvent) {
        val listBefore = completionList
        updateState(event)
        
        isValid = completionList.containsAll(listBefore)
    }

    fun feed(event: ItemSelectedByTypingEvent) {
        val id = event.selectedId
        
        isValid = allCompletionItems.find { it.id == id } != null && completionList.find { it == id } != null
        isFinished = true
    }
    
}



