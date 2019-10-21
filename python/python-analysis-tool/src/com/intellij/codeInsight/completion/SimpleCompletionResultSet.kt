package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.patterns.ElementPattern
import com.intellij.util.Consumer

class SimpleCompletionResultSet(matcher: PrefixMatcher, consumer: Consumer<in CompletionResult>, contributor: CompletionContributor):
  CompletionResultSet(matcher, consumer, contributor) {

  private val mySorter: CompletionSorter = object: CompletionSorter() {
    override fun weighBefore(beforeId: String, vararg weighers: LookupElementWeigher?): CompletionSorter {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun weighAfter(afterId: String, vararg weighers: LookupElementWeigher?): CompletionSorter {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun weigh(weigher: LookupElementWeigher?): CompletionSorter {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
  }

  override fun addElement(element: LookupElement) {
    val matched = CompletionResult.wrap(element, prefixMatcher, mySorter)
    if (matched != null) {
      passResult(matched)
    }
  }

  override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet {
    if (matcher == prefixMatcher) {
      return this
    }
    return SimpleCompletionResultSet(matcher, consumer, myContributor)
  }

  override fun withPrefixMatcher(prefix: String): CompletionResultSet = withPrefixMatcher(prefixMatcher.cloneWithPrefix(prefix))

  override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun addLookupAdvertisement(text: String) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun caseInsensitive(): CompletionResultSet {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun restartCompletionWhenNothingMatches() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}