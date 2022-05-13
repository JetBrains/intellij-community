package com.github.firsttimeinforever.mermaid.lang

import com.intellij.lang.Commenter

class MermaidCommenter: Commenter {
  override fun getLineCommentPrefix(): String {
    return "%%"
  }

  override fun getBlockCommentPrefix(): String? {
    return null
  }

  override fun getBlockCommentSuffix(): String? {
    return null
  }

  override fun getCommentedBlockCommentPrefix(): String? {
    return null
  }

  override fun getCommentedBlockCommentSuffix(): String? {
    return null
  }
}
