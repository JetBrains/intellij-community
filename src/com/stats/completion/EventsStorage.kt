package com.stats.completion

abstract class EventsStorage {

    abstract fun completionStarted()
    
    abstract fun completionCancelled()
    
    abstract fun itemSelected()
    
    abstract fun itemTyped()
    
}

class EventsStorageImpl : EventsStorage() {

    override fun completionStarted() = Unit

    override fun completionCancelled() = Unit

    override fun itemSelected() = Unit

    override fun itemTyped() {
        println("User typed correct value which we have shown on screen")
    }
    
}