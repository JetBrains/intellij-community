// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.detector.semantic.diff

import com.intellij.diff.DiffContentFactoryEx
import com.intellij.diff.DiffVcsDataKeys.REVISION_INFO
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.*
import com.intellij.diff.tools.combined.CombinedDiffRequest.InsertPosition
import com.intellij.diff.tools.combined.CombinedDiffRequest.NewChildDiffRequestData
import com.intellij.diff.tools.fragmented.UnifiedDiffChange
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffChange
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.calcRelativeToProjectPath
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.refactoring.detector.RefactoringDetectorBundle.Companion.message
import com.intellij.util.PathUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.research.refactorinsight.common.data.RefactoringEntry
import org.jetbrains.research.refactorinsight.common.data.RefactoringInfo
import org.jetbrains.research.refactorinsight.common.data.RefactoringLine
import org.jetbrains.research.refactorinsight.common.data.RefactoringType
import java.io.File

internal class SemanticInlayModel(private val combinedDiffViewerContext: UserDataHolder, private val viewer: DiffViewerBase) {
  private val semanticInlays = hashMapOf<Int, SemanticInlay>()

  private data class FragmentId(val changeIndex: Int, val title: @NlsSafe String)
  private val semanticFragmentsBlocks = hashMapOf<FragmentId, CombinedDiffBlock>()

  private val combinedDiffProcessor = viewer.context.getUserData(COMBINED_DIFF_PROCESSOR)!!

  private val isUnified = viewer is UnifiedDiffViewer

  companion object {
    fun install(combinedDiffViewerContext: UserDataHolder, viewer: DiffViewerBase) {
      SemanticInlayModel(combinedDiffViewerContext, viewer)
    }
  }

  init {
    viewer.addListener(SemanticEntriesListener())
  }

  private fun getCombinedDiffViewer() = combinedDiffProcessor.activeViewer as? CombinedDiffViewer

  private inner class SemanticEntriesListener : DiffViewerListener() {
    override fun onBeforeRediff() {
      super.onBeforeRediff()

      clearSemanticInlay()
    }

    override fun onAfterRediff() {
      super.onAfterRediff()

      val refactoringEntry = combinedDiffViewerContext.getUserData(REFACTORING_ENTRY) ?: return
      if (viewer is UnifiedDiffViewer) {
        viewer.diffChanges?.forEachIndexed { index, change -> installSemanticInlay(refactoringEntry, change, index) }
      }
      else if (viewer is SimpleDiffViewer) {
        viewer.diffChanges.forEachIndexed { index, change -> installSemanticInlay(refactoringEntry, change, index) }
      }
    }
  }

  private fun flipInlayState(index: Int) {
    (semanticInlays.getOrDefault(index, null) as? TextWithLinkInlay)?.flipState()
  }

  fun installSemanticInlay(refactoringEntry: RefactoringEntry, change: SimpleDiffChange, changeIndex: Int) {
    viewer as SimpleDiffViewer

    var inlaySide = Side.LEFT
    var refactoringInfo = viewer.request.path(inlaySide)?.let { changePath -> refactoringEntry.findInfo(changePath, change, inlaySide) }
    if (refactoringInfo == null) {
      inlaySide = Side.RIGHT
      refactoringInfo = viewer.request.path(inlaySide)?.let { changePath -> refactoringEntry.findInfo(changePath, change, inlaySide) }
    }

    if (refactoringInfo != null) {
      val editor = viewer.getEditor(inlaySide) as? EditorImpl ?: return

      createSemanticInlay(changeIndex, change.getStartLine(inlaySide), refactoringInfo, change.fragment, editor, inlaySide)
    }
  }

  fun installSemanticInlay(refactoringEntry: RefactoringEntry, change: UnifiedDiffChange, changeIndex: Int) {
    viewer as UnifiedDiffViewer

    val fragment = change.lineFragment
    val inlaySide = when {
      DiffUtil.getDiffType(fragment) == TextDiffType.DELETED -> Side.LEFT
      DiffUtil.getDiffType(fragment) == TextDiffType.INSERTED -> Side.RIGHT
      else -> Side.RIGHT
    }

    val changePath = viewer.request.path(inlaySide) ?: return

    val refactoringInfo = refactoringEntry.findInfo(viewer, changePath, change)

    if (refactoringInfo != null) {
      val editor = viewer.editor as? EditorImpl ?: return

      createSemanticInlay(changeIndex, change.line1, refactoringInfo, fragment, editor, inlaySide)
    }
  }

  private fun createSemanticInlay(changeIndex: Int,
                                  startLine: Int,
                                  refactoring: RefactoringInfo,
                                  fragment: LineFragment,
                                  editor: EditorImpl,
                                  inlaySide: Side) {
    val clickAction = {
      toggleSemanticDiffFragment(changeIndex, createFragmentDiffTitle(refactoring), refactoring, fragment, inlaySide)
    }
    val inlayText = createInlayText(fragment, refactoring)
    val inlayOffset = DiffUtil.getOffset(editor.document, startLine, 0)
    if (refactoring.isChanged) {
      semanticInlays[changeIndex] =
        TextWithLinkInlay(inlayText, message("show.diff.link"), message("hide.diff.link"), clickAction)
          .addTo(editor, inlayOffset)
    }
    else {
      semanticInlays[changeIndex] =
        TextInlay("$inlayText ${message("semantic.inlay.without.changes")}")
          .addTo(editor, inlayOffset)
    }
  }

  private fun toggleSemanticDiffFragment(changeIndex: Int,
                                         title: @NlsSafe String,
                                         info: RefactoringInfo,
                                         changeFragment: LineFragment,
                                         fragmentSide: Side) {
    val combinedDiffViewer = getCombinedDiffViewer() ?: return
    val fragmentId = FragmentId(changeIndex, title)
    if (semanticFragmentsBlocks.contains(fragmentId)) {
      semanticFragmentsBlocks[fragmentId]?.let(Disposer::dispose)
    }
    else {
      val parentBlock = combinedDiffViewer.getAllBlocks().find { it.content.viewer == viewer } ?: return
      val semanticFragmentDiffData =
        NewChildDiffRequestData(parentBlock.content.path, parentBlock.content.fileStatus, //TODO should be different?
                                position = InsertPosition(parentBlock.content.path, parentBlock.content.fileStatus, false))
      val fragmentDiffRequestProducer = FragmentDiffRequestProducer(title, info, viewer, changeIndex, changeFragment, fragmentSide)
      val semanticFragmentBlock = combinedDiffProcessor.addChildRequest(semanticFragmentDiffData, fragmentDiffRequestProducer) ?: return
      Disposer.register(semanticFragmentBlock, Disposable {
        semanticFragmentsBlocks.remove(fragmentId)
      })
      semanticFragmentsBlocks[fragmentId] = semanticFragmentBlock
      combinedDiffViewer.selectDiffBlock(semanticFragmentBlock, ScrollPolicy.DIFF_BLOCK) {}
    }
  }

  private inner class FragmentDiffRequestProducer(private val title: @Nls String,
                                                  private val info: RefactoringInfo,
                                                  private val inlayViewer: DiffViewer,
                                                  private val changeIndex: Int,
                                                  private val fragment: LineFragment,
                                                  private val fragmentSide: Side) : DiffRequestProducer {
    override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
      val fragmentEditor = inlayViewer.editor!!

      val (leftContent, rightContent) = createFragmentDiffContents(info, fragment, fragmentEditor, fragmentSide)

      return SemanticFragmentDiffRequest(title, leftContent, rightContent, info.nameBefore, info.nameAfter) { flipInlayState(changeIndex) }
    }

    override fun getName(): String = title
  }

  private fun createFragmentDiffContents(info: RefactoringInfo,
                                         fragment: LineFragment,
                                         fragmentEditor: EditorEx,
                                         fragmentSide: Side): Pair<DiffContent, DiffContent> {
    val diffContentFactory = DiffContentFactoryEx.getInstanceEx()
    val fragmentStartLine = fragmentSide.getStartLine(fragment)
    val fragmentEndLine = fragmentSide.getEndLine(fragment)

    fun empty() = with(diffContentFactory) { createEmpty() to createEmpty() }
    val combinedDiffViewer = getCombinedDiffViewer() ?: return empty()
    fun findContentBaseViewer(pathToFind: String) =
      combinedDiffViewer.getAllBlocks()
        .map { it.content }
        .find { it.path.path.contains(pathToFind) }?.let { it.path to it.viewer }

    val sideToFind = fragmentSide.other()
    val pathToFind = info.path(sideToFind)

    if (isUnified) {
      val (path, viewer) = findContentBaseViewer(pathToFind) ?: return empty()
      val unifiedViewer = (viewer as? UnifiedDiffViewer) ?: return empty() //TODO handle single side viewers

      val fragmentContent =
        diffContentFactory.create(DiffUtil.getLinesContent((this.viewer as UnifiedDiffViewer).getDocument(fragmentSide),
                                                           fragmentStartLine, fragmentEndLine).toString(), path.fileType)

      for (diffChange in unifiedViewer.diffChanges!!) {
        for (line in info.lineMarkings) {
          if (diffChange.includes(unifiedViewer, line, sideToFind)) {
            val line1 = unifiedViewer.transferLineFromOneside(sideToFind, diffChange.line1)
            val line2 = unifiedViewer.transferLineFromOneside(sideToFind, diffChange.line2)
            val foundedContent = diffContentFactory.create(
              DiffUtil.getLinesContent(unifiedViewer.getDocument(sideToFind), line1, line2).toString(), path.fileType)
            return if (fragmentSide.isLeft) fragmentContent to foundedContent else foundedContent to fragmentContent
          }
        }
      }
    }
    else {
      val (path, viewer) = findContentBaseViewer(pathToFind) ?: return empty()
      val simpleViewer = (viewer as? SimpleDiffViewer) ?: return empty() //TODO handle single side viewers

      val fragmentContent = diffContentFactory.create(
        DiffUtil.getLinesContent(fragmentEditor.document, fragmentStartLine, fragmentEndLine).toString(), path.fileType)

      for (diffChange in simpleViewer.diffChanges) {
        for (line in info.lineMarkings) {
          val foundedContent =
            if (diffChange.includes(line, sideToFind)) {
              diffContentFactory.create(
                DiffUtil.getLinesContent(viewer.getEditor(sideToFind).document, sideToFind.getStartLine(diffChange.fragment),
                                         sideToFind.getEndLine(diffChange.fragment)).toString(), path.fileType)
            } else continue

          return if (fragmentSide.isLeft) fragmentContent to foundedContent else foundedContent to fragmentContent
        }
      }
    }

    return empty()
  }

  private fun createInlayText(fragment: LineFragment, refactoringInfo: RefactoringInfo): @NlsSafe String {
    if (refactoringInfo.type == RefactoringType.MOVE_OPERATION) {
      val diffType = DiffUtil.getDiffType(fragment)
      val suffix =
        if (diffType == TextDiffType.DELETED) message("semantic.inlay.text.suffix.to") else message("semantic.inlay.text.suffix.from")
      val fileName = if (diffType == TextDiffType.DELETED) PathUtil.getFileName(refactoringInfo.rightPath)
      else PathUtil.getFileName(refactoringInfo.leftPath)

      return "${message("semantic.inlay.text.prefix.moved")} $suffix $fileName"
    }

    return refactoringInfo.type.getName()
  }

  private fun createFragmentDiffTitle(refactoringInfo: RefactoringInfo): @NlsSafe String {
    if (refactoringInfo.type == RefactoringType.MOVE_OPERATION) {
      val (leftPath, rightPath) = getLeftRightUIPresentablePaths(refactoringInfo.leftPath, refactoringInfo.rightPath)

      return if (leftPath == rightPath) message("semantic.inlay.text.moved.in", leftPath)
      else message("semantic.inlay.text.moved.from.to", leftPath, rightPath)
    }

    return refactoringInfo.type.getName()
  }

  private fun getLeftRightUIPresentablePaths(leftPath: String, rightPath: String): Pair<@NlsSafe String, @NlsSafe String> {
    val leftName = PathUtil.getFileName(leftPath)
    val rightName = PathUtil.getFileName(rightPath)
    if (leftName != rightName) return leftName to rightName

    fun String.relativeToProject(): String {
      return VfsUtil.findFileByIoFile(File(this), false)
               ?.let { calcRelativeToProjectPath(it, viewer.context.project!!) } ?: this
    }

    return leftPath.relativeToProject() to rightPath.relativeToProject()
  }

  private fun ContentDiffRequest.path(side: Side) = side.selectNotNull(contents).let { it.getUserData(REVISION_INFO)?.first }

  private fun RefactoringInfo.path(side: Side) = side.selectNotNull(leftPath, rightPath)

  private fun RefactoringEntry.findInfo(path: FilePath, change: SimpleDiffChange, side: Side): RefactoringInfo? {
    return refactorings.find { info: RefactoringInfo ->
      info.lineMarkings.find { line: RefactoringLine ->
        val infoPath = info.path(side)
        path.path == infoPath && change.includes(line, side)
      } != null
    }
  }

  private fun RefactoringEntry.findInfo(viewer: UnifiedDiffViewer, path: FilePath, change: UnifiedDiffChange): RefactoringInfo? {
    return refactorings.find { info: RefactoringInfo ->
      info.lineMarkings.find { line: RefactoringLine ->
        path.path == info.leftPath && change.includes(viewer, line, Side.LEFT)
        || path.path == info.rightPath && change.includes(viewer, line, Side.RIGHT)
      } != null
    }
  }

  private fun SimpleDiffChange.includes(line: RefactoringLine, side: Side): Boolean {
    val changeRange = getStartLine(side)..getEndLine(side)
    val start = side.select(line.leftStart, line.rightStart)
    val end = side.select(line.leftEnd, line.rightEnd)

    return changeRange.contains(start) || changeRange.contains(end)
  }

  private fun UnifiedDiffChange.includes(viewer: UnifiedDiffViewer, line: RefactoringLine, side: Side): Boolean {
    val changeRange = viewer.transferLineFromOneside(side, line1)..viewer.transferLineFromOneside(side, line2)
    val start = side.select(line.leftStart, line.rightStart)
    val end = side.select(line.leftEnd, line.rightEnd)

    return changeRange.contains(start) || changeRange.contains(end)
  }

  private fun clearSemanticInlay() {
    semanticInlays.values.forEach(Disposer::dispose)
    semanticInlays.clear()
    semanticFragmentsBlocks.values.forEach(Disposer::dispose)
    semanticFragmentsBlocks.clear()
  }
}
