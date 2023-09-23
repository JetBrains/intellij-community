package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.MergeableElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.searchEverywhereMl.semantics.providers.StreamSemanticItemsProvider
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface SearchEverywhereConcurrentElementsFetcher<I : MergeableElement, E : Any> {
  val itemsProvider: StreamSemanticItemsProvider<I>

  fun getDesiredResultsCount(): Int

  fun getPriorityThresholds(): Map<DescriptorPriority, Double>

  fun useReadAction(): Boolean

  fun defaultFetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in FoundItemDescriptor<E>>)

  fun prepareStandardDescriptor(descriptor: FoundItemDescriptor<E>,
                                knownItems: MutableList<FoundItemDescriptor<I>>,
                                mutex: ReentrantLock): FoundItemDescriptor<E>

  fun prepareSemanticDescriptor(descriptor: FoundItemDescriptor<I>,
                                knownItems: MutableList<FoundItemDescriptor<I>>,
                                mutex: ReentrantLock,
                                durationMs: Long): FoundItemDescriptor<E>?

  fun FoundItemDescriptor<I>.findPriority(): DescriptorPriority

  @RequiresBackgroundThread
  fun fetchElementsConcurrently(pattern: String,
                                progressIndicator: ProgressIndicator,
                                consumer: Processor<in FoundItemDescriptor<E>>) {
    val knownItems = mutableListOf<FoundItemDescriptor<I>>()
    val mutex = ReentrantLock()
    var ready = false
    val readyCondition = mutex.newCondition()
    syncSearchSettings()
    val standardContributorStartedMutex = ReentrantLock()
    var standardContributorStarted = false
    val standardContributorStartedCondition = standardContributorStartedMutex.newCondition()

    val searchStart = System.nanoTime()
    ApplicationManager.getApplication().apply {
      executeOnPooledThread {
        var foundItemsCount = 0
        val cachedDescriptors = mutableListOf<FoundItemDescriptor<I>>()

        val iterate = {
          val semanticMatches = itemsProvider.streamSearch(pattern, getPriorityThresholds()[DescriptorPriority.LOW])
          for (priority in ORDERED_PRIORITIES) {
            if (priority == DescriptorPriority.HIGH) {
              standardContributorStartedMutex.withLock {
                while (!standardContributorStarted) standardContributorStartedCondition.await()
              }
            }
            val iterator = if (priority == DescriptorPriority.HIGH) semanticMatches.iterator()
            else cachedDescriptors.filter { it.findPriority() == priority }.iterator()

            for (descriptor in iterator) {
              if (progressIndicator.isCanceled) break
              if (descriptor.findPriority() != priority) {
                cachedDescriptors.add(descriptor)
                continue
              }

              val durationMs = TimeoutUtil.getDurationMillis(searchStart)
              prepareSemanticDescriptor(descriptor, knownItems, mutex, durationMs)?.let {
                consumer.process(it)
                foundItemsCount++
              }
              if (priority != DescriptorPriority.HIGH && foundItemsCount >= getDesiredResultsCount()) break
            }
            if (progressIndicator.isCanceled || foundItemsCount >= getDesiredResultsCount()) break
          }
        }

        try {
          if (useReadAction()) runReadAction(iterate) else iterate()
        }
        finally {
          mutex.withLock {
            ready = true
            readyCondition.signal()
          }
        }
      }
    }

    var isFirstElement = true
    val allowSemanticSearch = {
      if (isFirstElement) {
        standardContributorStartedMutex.withLock {
          standardContributorStarted = true
          standardContributorStartedCondition.signal()
        }
        isFirstElement = false
      }
    }

    defaultFetchElements(pattern, progressIndicator) {
      allowSemanticSearch()
      consumer.process(prepareStandardDescriptor(it, knownItems, mutex))
    }
    allowSemanticSearch()
    mutex.withLock { while (!ready) readyCondition.await() } // account for possible spurious wakeup
  }

  fun syncSearchSettings() {}

  enum class DescriptorPriority { HIGH, MEDIUM, LOW }

  companion object {
    val ORDERED_PRIORITIES = listOf(DescriptorPriority.HIGH, DescriptorPriority.MEDIUM, DescriptorPriority.LOW)
  }
}