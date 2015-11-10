package com.stats.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import java.beans.PropertyChangeListener

class CompletionStatsRetriever(project: Project): AbstractProjectComponent(project) {
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup is LookupImpl) {
            val trackingListener = TrackingListener(lookup)
            lookup.addLookupListener(trackingListener)
            lookup.setPrefixChangeListener(trackingListener)
        }
    }

    override fun projectOpened() {
        val manager = LookupManager.getInstance(myProject)
        manager.addPropertyChangeListener(lookupTrackerInitializer)
    }
    
    override fun projectClosed() {
        val manager = LookupManager.getInstance(myProject)
        manager.removePropertyChangeListener(lookupTrackerInitializer)
    }
    
}

class TrackingListener(private val lookup: LookupImpl) : LookupListener, PrefixChangeListener {
    private var previous: LookupElement? = null
    private var ignoreItemChangedEvent = false
    
    override fun lookupCanceled(event: LookupEvent) = trackCancelled()

    override fun itemSelected(event: LookupEvent) = trackSelectedItem(event.item!!)

    override fun currentItemChanged(event: LookupEvent) {
        if (ignoreItemChangedEvent) return
        
        val current = event.item
        if (previous == current) return
        if (previous == null) {
            sessionStarted()
            previous = current
            return
        }
        
        val items = event.lookup.items
        val previousIndex = items.indexOf(previous)
        val currentIndex = items.indexOf(current)

        if (currentIndex > previousIndex) {
            trackDownPressed()
        }
        else {
            trackUpPressed()
        }
        
        previous = current
    }

    override fun beforeAppend(c: Char) {
        ignoreItemChangedEvent = true
    }
    
    override fun afterAppend(c: Char) {
        ignoreItemChangedEvent = false
        println("Appened: $c")
    }

    override fun beforeTruncate() {
        ignoreItemChangedEvent = true
    }

    override fun afterTruncate() {
        ignoreItemChangedEvent = false
        println("Truncated")
    }

    private fun sessionStarted() {
        println("Started")
    }
    
    private fun sessionFinished() {
        println("Finished")
    }

    private fun trackUpPressed() {
        println("UP PRESSED")
    }

    private fun trackDownPressed() {
        println("DOWN PRESSED")
    }
    
    private fun trackCancelled() {
        println("CANCELLED")
        sessionFinished()
    }

    private fun trackSelectedItem(item: LookupElement) {
        println("SELECTED ${item.lookupString}")
        sessionFinished()
    }


}