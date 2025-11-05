// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.lang.Commenter
import org.jetbrains.annotations.NonNls

@NonNls
private const val LINE_COMMENT_PREFIX: @NonNls String = "#"

class RequirementsCommenter : Commenter {

  override fun getLineCommentPrefix(): String = LINE_COMMENT_PREFIX

  override fun getBlockCommentPrefix(): String? = null

  override fun getBlockCommentSuffix(): String? = null

  override fun getCommentedBlockCommentPrefix(): String? = null

  override fun getCommentedBlockCommentSuffix(): String? = null
}