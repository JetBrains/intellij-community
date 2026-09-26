// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang

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
