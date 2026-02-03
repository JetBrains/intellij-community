package com.intellij.lang.properties.diff.data

import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.Side
import com.intellij.lang.properties.PropertiesLanguage
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory

internal class FileInfoHolder private constructor(private val left: FileInfo, private val right: FileInfo) {
  operator fun get(side: Side): FileInfo = when (side) {
    Side.LEFT -> left
    Side.RIGHT -> right
  }

  companion object {
    @JvmStatic
    internal fun create(project: Project, leftText: CharSequence, rightText: CharSequence): FileInfoHolder? {
      val left = createFileInfo(project, leftText) ?: return null
      val right = createFileInfo(project, rightText) ?: return null
      return FileInfoHolder(left, right)
    }

    private fun createFileInfo(project: Project, text: CharSequence): FileInfo? {
      val file = PsiFileFactory.getInstance(project).createFileFromText(PropertiesLanguage.INSTANCE, text)
      if (file !is PropertiesFile) return null
      val lineOffsets = LineOffsetsUtil.create(file.fileDocument)
      val propertiesList = file.properties
      val map = propertiesList.associate { property ->
        if (property !is Property) return null
        val key = property.unescapedKey ?: return null
        val value = property.unescapedValue ?: return null
        val startLine = lineOffsets.getLineNumber(property.textRange.startOffset)
        val endLine = lineOffsets.getLineNumber(property.textRange.endOffset)
        key to PropertyInfo(key, value, SemiOpenLineRange(startLine, endLine + 1))
      }
      if (map.size != propertiesList.size) return null
      return FileInfo(text, lineOffsets, map)
    }
  }
}