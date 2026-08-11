// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.markdown.linkgraph

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementManipulators
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.SingleEntryIndexer
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.jetbrains.annotations.ApiStatus
import java.io.DataInput
import java.io.DataOutput

@ApiStatus.Internal
data class MarkdownLinkIndexData(
  val destinations: List<String>,
)

@ApiStatus.Internal
class MarkdownLinkDestinationIndex : SingleEntryFileBasedIndexExtension<MarkdownLinkIndexData>() {
  override fun getName(): ID<Int, MarkdownLinkIndexData> = INDEX_NAME

  override fun getVersion(): Int = 1

  override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(MarkdownFileType.INSTANCE)

  override fun getIndexer(): SingleEntryIndexer<MarkdownLinkIndexData> = object : SingleEntryIndexer<MarkdownLinkIndexData>(false) {
    override fun computeValue(inputData: FileContent): MarkdownLinkIndexData? {
      val destinations = PsiTreeUtil.findChildrenOfType(inputData.psiFile, MarkdownLinkDestination::class.java)
        .asSequence()
        .filterNot { MarkdownLinkDefinition.isUnderCommentWrapper(it) }
        .mapNotNull(::extractDestinationText)
        .distinct()
        .toList()
      return destinations.takeIf { it.isNotEmpty() }?.let(::MarkdownLinkIndexData)
    }
  }

  override fun getValueExternalizer(): DataExternalizer<MarkdownLinkIndexData> = object : DataExternalizer<MarkdownLinkIndexData> {
    override fun save(out: DataOutput, value: MarkdownLinkIndexData) {
      DataInputOutputUtil.writeINT(out, value.destinations.size)
      for (destination in value.destinations) {
        IOUtil.writeUTF(out, destination)
      }
    }

    override fun read(input: DataInput): MarkdownLinkIndexData {
      val size = DataInputOutputUtil.readINT(input)
      val destinations = ArrayList<String>(size)
      repeat(size) {
        destinations.add(IOUtil.readUTF(input))
      }
      return MarkdownLinkIndexData(destinations)
    }
  }
}

internal fun getOutgoingMarkdownLinkDestinationTexts(project: Project, file: VirtualFile): List<String> {
  return FileBasedIndex.getInstance()
    .getSingleEntryIndexData(INDEX_NAME, file, project)
    ?.destinations
    .orEmpty()
}

private val INDEX_NAME: ID<Int, MarkdownLinkIndexData> = ID.create("markdown.link.destination.poc")

private fun extractDestinationText(destination: MarkdownLinkDestination): String? {
  val valueTextRange = ElementManipulators.getValueTextRange(destination)
  return StringUtil.nullize(valueTextRange.substring(destination.text).trim(), true)
}
