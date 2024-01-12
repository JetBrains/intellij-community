package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.psi.PsiElement
import com.intellij.searchEverywhereMl.semantics.contributors.SearchEverywhereConcurrentElementsFetcher.Companion.ORDERED_PRIORITIES
import com.intellij.searchEverywhereMl.semantics.utils.attachPsiPresentation
import org.jetbrains.annotations.ApiStatus
import com.intellij.searchEverywhereMl.semantics.contributors.SearchEverywhereConcurrentElementsFetcher.DescriptorPriority

@ApiStatus.Experimental
interface SearchEverywhereConcurrentPsiElementsFetcher : SearchEverywhereConcurrentElementsFetcher<PsiItemWithSimilarity<*>, Any> {
  val psiElementsRenderer: SearchEverywherePsiRenderer

  override val useReadAction
    get() = true

  override val desiredResultsCount
    get() = DESIRED_RESULTS_COUNT

  override val priorityThresholds
    get() = PRIORITY_THRESHOLDS

  override fun prepareStandardDescriptor(descriptor: FoundItemDescriptor<Any>,
                                         knownItems: MutableList<FoundItemDescriptor<PsiItemWithSimilarity<*>>>): () -> FoundItemDescriptor<Any> {
    val item = PsiItemWithSimilarity(descriptor.item)
    return {
      val equal = knownItems.firstOrNull { checkItemsEqual(it.item.value, item.value) }
      if (equal != null) {
        if (equal.item.shouldBeMergedIntoAnother()) {
          // slightly increase the weight to replace
          FoundItemDescriptor(item.mergeWith(equal.item) as PsiItemWithSimilarity<*>, descriptor.weight)
        }
        else descriptor
      }
      else {
        knownItems.add(FoundItemDescriptor(item, descriptor.weight))
        FoundItemDescriptor(item, descriptor.weight)
      }
    }
  }

  override fun prepareSemanticDescriptor(descriptor: FoundItemDescriptor<PsiItemWithSimilarity<*>>,
                                         knownItems: MutableList<FoundItemDescriptor<PsiItemWithSimilarity<*>>>,
                                         durationMs: Long): () -> FoundItemDescriptor<Any> {
    val element = descriptor.item.value
    val foundElement = if (element is PsiElement) attachPsiPresentation(element, psiElementsRenderer) else element
    val newItem = PsiItemWithSimilarity(foundElement, descriptor.item.similarityScore)

    return {
      val equal = knownItems.firstOrNull { checkItemsEqual(it.item.value, foundElement) }
      if (equal != null) {
        // slightly increase the weight to replace
        FoundItemDescriptor(equal.item.mergeWith(newItem) as PsiItemWithSimilarity<*>, equal.weight + 1)
      }
      else {
        knownItems.add(descriptor)
        FoundItemDescriptor(newItem, descriptor.weight)
      }
    }
  }

  private fun checkItemsEqual(lhs: Any, rhs: Any): Boolean {
    val lhsFile = PSIPresentationBgRendererWrapper.toPsi(lhs)
    val rhsFile = PSIPresentationBgRendererWrapper.toPsi(rhs)
    return lhsFile != null && rhsFile != null && lhsFile == rhsFile
  }

  override fun ScoredText.findPriority(): DescriptorPriority {
    return ORDERED_PRIORITIES.first { similarity > priorityThresholds[it]!! }
  }

  companion object {
    val PRIORITY_THRESHOLDS = (ORDERED_PRIORITIES zip listOf(0.68, 0.5, 0.4)).toMap()
    private const val DESIRED_RESULTS_COUNT = 10
  }
}