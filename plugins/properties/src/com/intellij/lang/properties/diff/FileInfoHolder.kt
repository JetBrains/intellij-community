// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.diff

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.Side
import com.intellij.lang.properties.PropertiesLanguage
import com.intellij.lang.properties.diff.data.FileInfo
import com.intellij.lang.properties.diff.util.toTextRange
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory

internal class FileInfoHolder private constructor(leftFileInfo: FileInfo, rightFileInfo: FileInfo) : DiffHolderBase<FileInfo>(leftFileInfo, rightFileInfo) {
  companion object {
    @JvmStatic
    internal fun create(project: Project, leftText: CharSequence, rightText: CharSequence, lineFragmentList: List<LineFragment>): FileInfoHolder {
      return FileInfoHolder(
          createFileInfo(project, leftText, lineFragmentList, Side.LEFT),
          createFileInfo(project, rightText, lineFragmentList, Side.RIGHT)
        )
    }

    private fun createFileInfo(project: Project, text: CharSequence, lineFragmentList: List<LineFragment>, side: Side): FileInfo {
      val file = PsiFileFactory.getInstance(project).createFileFromText(PropertiesLanguage.INSTANCE, text)
      val lineOffsets = LineOffsetsUtil.create(file.fileDocument)
      return FileInfo(file, text, lineOffsets, lineFragmentList.toTextRange(side))
    }
  }
}