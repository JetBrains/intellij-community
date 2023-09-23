package com.intellij.searchEverywhereMl.semantics.listeners

import com.intellij.util.messages.Topic


interface SemanticIndexingFinishListener {
  fun finished(indexId: String? = null)

  companion object {
    @Topic.ProjectLevel
    val FINISHED: Topic<SemanticIndexingFinishListener> = Topic(
      SemanticIndexingFinishListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}