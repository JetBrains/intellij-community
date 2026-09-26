// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull

/**
 * [nameWithSuffix] will create `[nameNoExt]`N`.[ext] where `N > 0` if file already exists
 */
data class TemplateFileName(private val nameNoExt: @NotNull String, private val ext: @NonNls String) {

  fun nameWithSuffix(suffixCount: Int): @NlsSafe String {
    val suffix = if (suffixCount == 0) "" else suffixCount.toString()
    return "$nameNoExt$suffix.$ext"
  }

  companion object {
    /**
     * Creates instance from `file.ext` ie `notebook.ipynb`
     */
    fun parse(fileNameWithExt: @NonNls String): TemplateFileName {
      val filePaths = fileNameWithExt.split(".")
      assert(filePaths.size == 2) { "$fileNameWithExt must be file.ext" }
      return TemplateFileName(filePaths[0], filePaths[1])
    }
  }
}