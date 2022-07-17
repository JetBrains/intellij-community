package com.intellij.refactoring.detector.semantic.diff

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.util.NlsContexts

class SemanticFragmentDiffRequest(title: @NlsContexts.DialogTitle String,
                                  content1: DiffContent,
                                  content2: DiffContent,
                                  title1: @NlsContexts.Label String,
                                  title2: @NlsContexts.Label String,
                                  val closeAction: () -> Unit) : SimpleDiffRequest(title, content1, content2, title1, title2) {
  init {
    putUserData(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, true)
  }

  override fun getTitle(): String = super.getTitle()!!
}
