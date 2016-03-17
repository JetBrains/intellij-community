package com.intellij.stats.completion.validator

import com.intellij.stats.completion.events.*



class CompletionStartedValidator(private val initialEvent: CompletionStartedEvent) : LogEventVisitor(), TransitionValidator {

    override var isValid: Boolean = false

    override fun visit(event: TypeEvent) {
        isValid = true
    }

    override fun visit(event: DownPressedEvent) {
        val newPosition = initialEvent.currentPosition + 1
        isValid = event.currentPosition == newPosition
    }

    override fun visit(event: UpPressedEvent) {
        val totalItems = initialEvent.completionListLength
        val newPosition = (initialEvent.currentPosition - 1 + totalItems) % totalItems   
        isValid = event.currentPosition == newPosition
    }

    override fun visit(event: BackspaceEvent) {
        isValid = true
    }

    override fun visit(event: CompletionCancelledEvent) {
        isValid = true
    }

    override fun visit(event: ExplicitSelectEvent) {
        isValid = event.currentPosition == initialEvent.currentPosition 
    }
    
}
