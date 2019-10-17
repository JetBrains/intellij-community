package com.intellij.codeInsight.completion

import com.intellij.util.Consumer

class SingleCompletionService: CompletionService() {
  override fun setAdvertisementText(text: String?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createResultSet(parameters: CompletionParameters?,
                               consumer: Consumer<in CompletionResult>?,
                               contributor: CompletionContributor,
                               matcher: PrefixMatcher?): CompletionResultSet {
    return SimpleCompletionResultSet(matcher!!, consumer!!, contributor)
  }

  override fun suggestPrefix(parameters: CompletionParameters?): String {
    val file = parameters?.originalFile
    val element = file?.findElementAt(parameters.offset)
    return element?.text ?: ""
  }

  override fun createMatcher(prefix: String?, typoTolerant: Boolean): PrefixMatcher = prefix?.let { PlainPrefixMatcher(it) } ?: PrefixMatcher.ALWAYS_TRUE

  override fun getCurrentCompletion(): CompletionProcess? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun defaultSorter(parameters: CompletionParameters?, matcher: PrefixMatcher?): CompletionSorter {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun emptySorter(): CompletionSorter {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}