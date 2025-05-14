package com.intellij.lang.properties.merge

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.fragments.MergeLineFragmentImpl
import com.intellij.diff.merge.LangSpecificMergeConflictResolverWrapper
import com.intellij.diff.merge.MergeModelBase
import com.intellij.diff.merge.TextMergeChange
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.ThreeSide
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.testFramework.fixtures.BasePlatformTestCase


class PropertiesMergeConflictResolverTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String {
    return PluginPathManager.getPluginHomePath("properties") + "/tests/testData/merge/conflictResolver/"
  }

  fun testSimpleMerge() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true)
    )
  )

  fun testSimpleMergeKeysOnly() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true)
    )
  )

  fun testMergeWithSameKey() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 2, true),
    )
  )

  fun testDoNotMergeIncompatibleChangeLeft() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 1, 0, 1, false),
    )
  )

  fun testDoNotMergeIncompatibleChangeRight() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 1, 0, 2, false),
    )
  )

  fun testHashCommentsSupported() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 1, true)
    )
  )

  fun testCommentsInDifferentChunksLeft() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 1, true),
      MergeLineFragmentWithMetaData.create(3, 4, 0, 0, 2, 3, true)
    )
  )

  fun testCommentsInDifferentChunksRight() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 2, true),
      MergeLineFragmentWithMetaData.create(2, 3, 0, 0, 3, 4, true)
    )
  )

  fun testMergeLeftSideOnly() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 2, true)
    )
  )

  fun testMergeRightSideOnly() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 2, true)
    )
  )

  fun testExclamationCommentsSupported() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 1, true)
    )
  )

  fun testMultipleConflicts() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true),
      MergeLineFragmentWithMetaData.create(2, 3, 1, 1, 2, 3, true),
    ))

  fun testChangeInMultilinePropertyIsIgnored() = doTest(listOf(
    MergeLineFragmentWithMetaData.create(1, 2, 1, 2, 1, 2, false),
    MergeLineFragmentWithMetaData.create(3, 4, 3, 4, 3, 4, false),
  )
  )

  fun testSortsProperties() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 2, true),
    )
  )

  fun testOuterDuplicatesLeftToRight() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, false),
      MergeLineFragmentWithMetaData.create(2, 3, 1, 1, 2, 3, true),
    )
  )

  fun testOuterDuplicatesRightToLeft() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, false),
      MergeLineFragmentWithMetaData.create(2, 3, 1, 1, 2, 3, true),
    )
  )

  fun testSortsPropertiesKeysOnly() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 2, true),
    )
  )

  fun testIgnoreEmptyLines() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 3, 0, 0, 0, 4, true),
    )
  )

  fun testMergeWithMultilineProperties() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 3, 0, 0, 0, 3, true),
    )
  )

  fun testIgnoreSpacesAroundKey() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true),
    )
  )

  fun testIgnoreSpacesAroundDelimiter() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true),
    )
  )

  fun testIgnoreUnescapedKey() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 3, 0, 0, 0, 1, true),
    )
  )

  fun testIgnoreUnescapedValue() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 3, true),
    )
  )

  fun testRespectSpacesInTheEndOfLine() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, false),
    )
  )

  fun testIgnoreSpacesInTheEndOfLineKeysOnly() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true),
    )
  )

  fun testIgnoreDelimiters() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true),
    )
  )

  fun testDoNotMergeWithSplitedComment() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 4, 0, 0, 0, 1, false),
    )
  )

  fun testDoNotMergeWithSplitedCommentAfterProperty() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(1, 5, 1, 1, 1, 1, false),
    )
  )

  fun testMergeWithCommentAndSpaces() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 4, 0, 0, 0, 1, true),
    )
  )

  fun testMergeWithCommentAndSpacesAfterProperty() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(1, 5, 1, 1, 1, 2, true),
    )
  )

  fun testDoNotMergeDifferentComment() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 2, false),
    )
  )

  fun testMergeWithEmptyCommentLeft() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 2, true),
    )
  )

  fun testMergeWithEmptyCommentRight() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 1, true),
    )
  )

  fun testDoNotMergePartiallyModifiedComment() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(1, 3, 0, 0, 0, 3, false),
    )
  )

  fun testMergeWithCommentKeysOnly() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 0, 0, 2, true),
    )
  )

  fun testDoNotMergeWithInconsistentCommentLeft() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 2, 0, 2, 0, 1, false),
    )
  )

  fun testDoNotMergeWithInconsistentCommentRight() = doTest(
    listOf(
      MergeLineFragmentWithMetaData.create(0, 1, 0, 2, 0, 2, false),
    )
  )

  fun testMergingRespectsCodeStyleSettings() {
    val settings = PropertiesCodeStyleSettings.getInstance(project)
    val oldDelimiterCode = settings.KEY_VALUE_DELIMITER_CODE
    val keepSpaces = settings.SPACES_AROUND_KEY_VALUE_DELIMITER
    try {
      settings.KEY_VALUE_DELIMITER_CODE = 1
      settings.SPACES_AROUND_KEY_VALUE_DELIMITER = true
      doTest(
        listOf(
          MergeLineFragmentWithMetaData.create(0, 1, 0, 0, 0, 1, true),
        )
      )
    } finally {
      settings.KEY_VALUE_DELIMITER_CODE = oldDelimiterCode
      settings.SPACES_AROUND_KEY_VALUE_DELIMITER = keepSpaces
    }
  }

  private fun doTest(fragmentWithMetaDataList: List<MergeLineFragmentWithMetaData> = emptyList()) {
    val prefix = getTestName(true)
    val fileList = myFixture.configureByFiles("$prefix/left.properties", "$prefix/base.properties", "$prefix/right.properties").toList()

    val baseFile = fileList[1]
    val model = MockMergeThreeSideModel(project, baseFile.fileDocument, fragmentWithMetaDataList)
    Disposer.register(testRootDisposable, model)

    val diffContentFactory = DiffContentFactory.getInstance()

    val diffContentList = fileList.map {
      diffContentFactory.create(it.project, it.fileDocument, it.fileType)
    }

    Registry.get("semantic.merge.conflict.resolution").setValue(true, testRootDisposable)
    val resolver = LangSpecificMergeConflictResolverWrapper(project, diffContentList)
    val lineOffsets: List<LineOffsets> = fileList.map { file -> LineOffsetsUtil.create(file.fileDocument) }

    resolver.init(lineOffsets, fragmentWithMetaDataList.map { it.fragment }, fileList)

    fragmentWithMetaDataList.forEachIndexed { index, fragmentMetaData ->
      assertEquals(fragmentMetaData.canResolve, resolver.canResolveConflictSemantically(index))
      if (fragmentMetaData.canResolve) {
        modifyDocumentWithChange(resolver, model, index)
      }
    }

    myFixture.openFileInEditor(baseFile.virtualFile)
    myFixture.checkResultByFile("$prefix/result.properties")
  }

  private fun modifyDocumentWithChange(
    resolver: LangSpecificMergeConflictResolverWrapper,
    model: MockMergeThreeSideModel,
    index: Int,
  ) {
    val newContent = resolver.getResolvedConflictContent(index)
    assertNotNull(newContent)

    val newContentLines: Array<String> = LineTokenizer.tokenize(newContent, false)
    model.executeMergeCommand(null, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, false, index) {
      model.replaceChange(index, newContentLines.toList())
    }
  }

  private data class MergeLineFragmentWithMetaData(val fragment: MergeLineFragment, val canResolve: Boolean) {
    companion object {
      fun create(startLine1: Int, endLine1: Int, startLine2: Int, endLine2: Int, startLine3: Int, endLine3: Int, canResolve: Boolean): MergeLineFragmentWithMetaData {
        return MergeLineFragmentWithMetaData(
          MergeLineFragmentImpl(startLine1, endLine1, startLine2, endLine2, startLine3, endLine3),
          canResolve
        )
      }
    }
  }

  private class MockMergeThreeSideModel(project: Project, document: Document, changeList: List<MergeLineFragmentWithMetaData>) : MergeModelBase<TextMergeChange.State>(project, document) {
    init {
      setChanges(
        changeList.map { LineRange(it.fragment.getStartLine(ThreeSide.BASE), it.fragment.getEndLine(ThreeSide.BASE)) }
      )
    }

    override fun reinstallHighlighters(index: Int) = Unit

    override fun storeChangeState(index: Int): TextMergeChange.State? = null
  }
}