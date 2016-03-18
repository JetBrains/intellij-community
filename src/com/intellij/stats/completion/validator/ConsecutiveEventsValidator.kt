package com.intellij.stats.completion.validator


//class CompletionStartedValidator(private val initialEvent: CompletionStartedEvent) : LogEventVisitor(), TransitionValidator {
//    
//    override var completionState = CompletionState(initialEvent)
//    
//    override fun visit(event: TypeEvent) {
//        
//    }
//
//    override fun visit(event: DownPressedEvent) {
//        val newPosition = initialEvent.currentPosition + 1
//        isValid = event.currentPosition == newPosition
//        updateCompletionState(event)
//    }
//
//    override fun visit(event: UpPressedEvent) {
//        val totalItems = initialEvent.completionListLength
//        val newPosition = (initialEvent.currentPosition - 1 + totalItems) % totalItems   
//        isValid = event.currentPosition == newPosition
//        updateCompletionState(event)
//    }
//
//    override fun visit(event: BackspaceEvent) {
//        isValid = event.completionListIds.isNotEmpty()
//        updateCompletionState(event)
//    }
//
//    override fun visit(event: CompletionCancelledEvent) {
//        isValid = true
//        completionState.isFinished = isValid
//    }
//
//    override fun visit(event: ExplicitSelectEvent) {
//        isValid = event.currentPosition == initialEvent.currentPosition
//        completionState.isFinished = isValid
//    }
//    
//}


//class TypedEventValidator(private val initialEvent: TypeEvent) : LogEventVisitor(), TransitionValidator {
//
//    override var isValid = false
//
//    override fun visit(event: TypeEvent) {
//        isValid = true
//    }
//
//    override fun visit(event: DownPressedEvent) {
//        val initialPosition = initialEvent.currentPosition
//        isValid = initialPosition == event.currentPosition
//    }
//
//    override fun visit(event: UpPressedEvent) {
//
//    }
//
//    override fun visit(event: BackspaceEvent) {
//
//    }
//
//    override fun visit(event: CompletionCancelledEvent) {
//
//    }
//
//    override fun visit(event: ExplicitSelectEvent) {
//
//    }
//
//}

