package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.MergeableElement
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.progress.*
import com.intellij.searchEverywhereMl.semantics.providers.StreamSemanticItemsProvider
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SearchEverywhereConcurrentElementsFetcher<I : MergeableElement, E : Any> {
  val itemsProvider: StreamSemanticItemsProvider<I>

  val useReadAction: Boolean
    get() = false

  val desiredResultsCount: Int

  val priorityThresholds: Map<DescriptorPriority, Double>

  fun defaultFetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in FoundItemDescriptor<E>>)

  fun prepareStandardDescriptor(descriptor: FoundItemDescriptor<E>,
                                knownItems: MutableList<FoundItemDescriptor<I>>): () -> FoundItemDescriptor<E>

  fun prepareSemanticDescriptor(descriptor: FoundItemDescriptor<I>,
                                knownItems: MutableList<FoundItemDescriptor<I>>,
                                durationMs: Long): () -> FoundItemDescriptor<E>?

  fun FoundItemDescriptor<I>.findPriority(): DescriptorPriority

  @RequiresBackgroundThread
  fun fetchElementsConcurrently(pattern: String,
                                progressIndicator: ProgressIndicator,
                                consumer: Processor<in FoundItemDescriptor<E>>): Unit = runBlockingCancellable {
    syncSearchSettings()
    val knownItems = mutableListOf<FoundItemDescriptor<I>>()

    val mutex = Mutex()
    val readyChannel = Channel<Unit>()
    val standardContributorStartedChannel = Channel<Unit>()

    val searchStart = System.nanoTime()
    launch {
      var foundItemsCount = 0
      val cachedDescriptors = mutableListOf<FoundItemDescriptor<I>>()

      suspend fun iterate() = coroutineScope {
        val semanticMatches = itemsProvider.streamSearchIfEnabled(pattern, priorityThresholds[DescriptorPriority.LOW])
        for (priority in ORDERED_PRIORITIES) {
          val iterator = if (priority == DescriptorPriority.HIGH) semanticMatches.iterator()
          else cachedDescriptors.filter { it.findPriority() == priority }.iterator()

          while (iterator.hasNext()) {
            ensureActive()
            val descriptor = iterator.next()
            if (priority == DescriptorPriority.HIGH && descriptor.findPriority() != priority) {
              cachedDescriptors.add(descriptor)
              continue
            }

            val prepareDescriptor = prepareSemanticDescriptor(descriptor, knownItems, TimeoutUtil.getDurationMillis(searchStart))
            mutex.withLock { prepareDescriptor() }?.let {
              consumer.process(it)
              foundItemsCount++
            }
            if (priority != DescriptorPriority.HIGH && foundItemsCount >= desiredResultsCount) break
          }
          if (progressIndicator.isCanceled || foundItemsCount >= desiredResultsCount) break
        }
      }

      standardContributorStartedChannel.receiveCatching()
      if (useReadAction) readActionBlocking { runBlockingCancellable { iterate() } } else iterate()
      readyChannel.close()
    }

    coroutineToIndicator {
      defaultFetchElements(pattern, progressIndicator) {
        standardContributorStartedChannel.close()
        val prepareDescriptor = prepareStandardDescriptor(it, knownItems)
        val descriptor = runBlockingCancellable { mutex.withLock { prepareDescriptor() } }
        consumer.process(descriptor)
      }
    }
    standardContributorStartedChannel.close()
    readyChannel.receiveCatching()
  }

  fun syncSearchSettings() {}

  enum class DescriptorPriority { HIGH, MEDIUM, LOW }

  companion object {
    val ORDERED_PRIORITIES = listOf(DescriptorPriority.HIGH, DescriptorPriority.MEDIUM, DescriptorPriority.LOW)
  }
}