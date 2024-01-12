package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.MergeableElement
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.progress.*
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.searchEverywhereMl.semantics.providers.StreamSemanticItemsProvider
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
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

  fun ScoredText.findPriority(): DescriptorPriority

  @RequiresBackgroundThread
  fun fetchElementsConcurrently(pattern: String,
                                progressIndicator: ProgressIndicator,
                                consumer: Processor<in FoundItemDescriptor<E>>) {
    runBlockingCancellable {
      syncSearchSettings()
      val knownItems = mutableListOf<FoundItemDescriptor<I>>()
      val mutex = Mutex()

      val standardSearchJob = launch {
        coroutineToIndicator {
          defaultFetchElements(pattern, progressIndicator) {
            val prepareDescriptor = prepareStandardDescriptor(it, knownItems)
            val descriptor = runBlockingCancellable { mutex.withLock { prepareDescriptor() } }
            consumer.process(descriptor)
          }
        }
      }

      val searchStart = System.nanoTime()
      launch {
        var foundItemsCount = 0
        val cachedMatches = mutableListOf<ScoredText>()

        suspend fun iterate() {
          val semanticMatches = itemsProvider.searchIfEnabled(pattern, priorityThresholds[DescriptorPriority.LOW])
          if (semanticMatches.isEmpty()) return
          standardSearchJob.join()
          for (priority in ORDERED_PRIORITIES) {
            val iterator = if (priority == DescriptorPriority.HIGH) semanticMatches.iterator()
            else cachedMatches.filter { it.findPriority() == priority }.iterator()

            while (iterator.hasNext()) {
              ensureActive()
              val match = iterator.next()
              if (priority == DescriptorPriority.HIGH && match.findPriority() != priority) {
                cachedMatches.add(match)
                continue
              }

              for (descriptor in itemsProvider.createItemDescriptors(match.text, match.similarity, pattern)) {
                val prepareDescriptor = prepareSemanticDescriptor(descriptor, knownItems, TimeoutUtil.getDurationMillis(searchStart))
                mutex.withLock { prepareDescriptor() }?.let {
                  consumer.process(it)
                  foundItemsCount++
                }
                if (priority != DescriptorPriority.HIGH && foundItemsCount >= desiredResultsCount) return
              }
            }
            if (progressIndicator.isCanceled || foundItemsCount >= desiredResultsCount) return
          }
        }

        if (useReadAction) readActionBlocking { runBlockingCancellable { iterate() } } else iterate()
      }
    }
  }

  fun syncSearchSettings() {}

  enum class DescriptorPriority { HIGH, MEDIUM, LOW }

  companion object {
    val ORDERED_PRIORITIES = listOf(DescriptorPriority.HIGH, DescriptorPriority.MEDIUM, DescriptorPriority.LOW)
  }
}