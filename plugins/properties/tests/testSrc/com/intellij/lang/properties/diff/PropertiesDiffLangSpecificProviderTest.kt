package com.intellij.lang.properties.diff

import com.intellij.diff.contents.DocumentContentImpl
import com.intellij.diff.fragments.DiffFragmentImpl
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.text.SmartTextDiffProvider
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertiesDiffLangSpecificProviderTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String? {
    return PluginPathManager.getPluginHomePath("properties") + "/tests/testData/diff/languageSpecificChanges"
  }

  fun testSpacesBeforeSeparator() = doTest()

  fun testSpacesAfterSeparator() = doTest()

  fun testSpacesBeforeAndAfterSeparator() = doTest()

  fun testChangeKeyValueSeparator() = doTest()

  fun testSpacesInBeginningOfLine() = doTest()

  fun testSpacesInBeginningOfLineKeysOnly() = doTest()

  fun testEscapedCharactersInKey() = doTest()

  fun testSpacesInEndOfLine() = doTest(
    listOf(
      LineFragmentImpl(0, 1, 0, 1, 0, 4, 0, 8)
    )
  )

  fun testSpacesInEndOfLineKeysOnly() = doTest()

  fun testReorderingMixed() = doTest()

  fun testReorderingKeysOnly() = doTest()

  fun testReorderingSimple() = doTest()

  fun testReorderingMultiline() = doTest()

  fun testReorderingMultipleProperties() = doTest()

  fun testReorderingWithNewProperties() = doTest(
    listOf(
      LineFragmentImpl(1, 2, 2, 3, 12, 24, 24, 38),
      LineFragmentImpl(3, 4, 3, 4, 36, 48, 38, 52),
      LineFragmentImpl(4, 5, 4, 5, 48, 59,  52, 65),
    )
  )

  fun testReorderingWithSingleValueChange() = doTest(
    listOf(
      LineFragmentImpl(0, 1, 1, 2, 0, 12, 12, 28),
    )
  )

  fun testReorderingWithMultipleValueChanges() = doTest(
    listOf(
      LineFragmentImpl(2, 3, 0, 1, 24, 35, 0, 16),
      LineFragmentImpl(0, 1, 2, 3, 0, 12, 28, 43),
    )
  )

  fun testIgnoreEmptyLinesWithProperties() = doTest()

  fun testIgnoreEmptyLinesAddition() = doTest()

  fun testIgnoreEmptyLinesDeletion() = doTest()

  fun testIgnoreEmptyLinesReordering() = doTest()

  fun testDuplicateKeyBefore() = doTest()

  fun testDuplicateKeyAfter() = doTest()

  fun testNewOptionsWithChangesBefore() = doTest(
    listOf(
      LineFragmentImpl(1, 1, 0, 2, 17, 17, 0, 46),
      LineFragmentImpl(0, 1, 2, 3, 0, 17, 46, 63),
    )
  )

  fun testNewOptionsWithChangesAfter() = doTest(
    listOf(
      LineFragmentImpl(1, 1, 1, 3, 17, 17, 18, 63),
      LineFragmentImpl(0, 1, 0, 1, 0, 17, 0, 18),
    )
  )


  fun testNewOptionsWithChangesMixed() = doTest(
    listOf(
      LineFragmentImpl(1, 1, 0, 2, 17, 17, 0, 46),
      LineFragmentImpl(1, 1, 3, 5, 17, 17, 64, 109),
      LineFragmentImpl(0, 1, 2, 3, 0, 17, 46, 64),
    )
  )

  fun testHighlightingByWord() = doTest(
    expectedLineFragmentList = listOf(
      LineFragmentImpl(1, 2, 0, 1, 12, 23, 0, 16,
                       listOf(
                         DiffFragmentImpl(5, 5, 5, 9),
                         DiffFragmentImpl(11, 11, 15, 16),
                       )),
    ),
    settings = createSettings(HighlightPolicy.BY_WORD)
  )


  fun testHighlightingByCharacter() = doTest(
    expectedLineFragmentList = listOf(
      LineFragmentImpl(1, 2, 0, 1, 12, 23, 0, 15,
                       listOf(
                         DiffFragmentImpl(7, 7, 7, 8),
                         DiffFragmentImpl(11, 11, 12, 15),
                       )),
    ),
    settings = createSettings(HighlightPolicy.BY_CHAR)
  )

  fun testHighlightingByWordSplit() = doTest(
    expectedLineFragmentList = listOf(
      LineFragmentImpl(1, 2, 0, 1, 12, 23, 0, 16,
                       listOf(
                         DiffFragmentImpl(5, 5, 5, 9),
                         DiffFragmentImpl(11, 11, 15, 16),
                       )),
    ),
    settings = createSettings(HighlightPolicy.BY_WORD)
  )

  fun testChangeMultiline() = doTest(
    listOf(
      LineFragmentImpl(0, 1, 0, 1, 0, 23, 0, 29),
      LineFragmentImpl(2, 3, 2, 3, 34, 45, 40, 55),
    )
  )

  fun testChangeMultilineWithReorderingNoChange() = doTest(
    listOf(
      LineFragmentImpl(1, 2, 0, 1, 10,33, 0, 29),
      LineFragmentImpl(4,5, 3, 4, 55, 63, 51, 64),
    )
  )

  fun testChangeMultilineWithReorderingWithChange() = doTest(
    listOf(
      LineFragmentImpl(1, 2, 0, 1, 10,33, 0, 29),
      LineFragmentImpl(4,5, 3, 4, 55, 63, 51, 64),
      LineFragmentImpl(0,1, 4, 5, 0, 10, 64, 77),
    )
  )

  fun testIgnoreCommentsHashAddition() = doTest()

  fun testIgnoreCommentsHashDeletion() = doTest()

  fun testIgnoreCommentsExclamationMarkAddition() = doTest()

  fun testIgnoreCommentsExclamationMarkDeletion() = doTest()

  fun testIgnoreCommentsHashReordering() = doTest()

  fun testIgnoreCommentsExclamationMarkReordering() = doTest()

  fun testHashCommentAfterProperty() = doTest(
    listOf(
      LineFragmentImpl(0, 1, 0, 1, 0, 11, 0, 25),
    )
  )

  fun testExclamationMarkCommentAfterProperty() = doTest(
    listOf(
      LineFragmentImpl(0, 1, 0, 1, 0, 11, 0, 25),
    )
  )

  private fun doTest(expectedLineFragmentList: List<LineFragment> = emptyList(), settings: TextDiffSettingsHolder.TextDiffSettings = createSettings()) = Registry.get("diff.semantic.highlighting").withValue(true) {
    val testName = getTestName(true)
    val filesList = myFixture.configureByFiles("$testName/before.properties", "$testName/after.properties")
    assertEquals(2, filesList.size)
    val beforeFile = filesList.first()
    val afterFile = filesList.last()

    val request = createDiffRequest(beforeFile, afterFile)

    val settings = settings
    val provider = SmartTextDiffProvider.create(myFixture.project, request, settings, Runnable { }, testRootDisposable)

    val result = provider.compare(beforeFile.text, afterFile.text, DumbProgressIndicator.INSTANCE)

    if (expectedLineFragmentList.isNotEmpty()) {
      assertNotNull(result)
      assertEquals(expectedLineFragmentList, result)
    } else {
      assertNull(result)
    }
  }

  private fun createSettings(highlightPolicy: HighlightPolicy = HighlightPolicy.BY_LINE): TextDiffSettingsHolder.TextDiffSettings {
    val settings = TextDiffSettingsHolder.TextDiffSettings()
    settings.ignorePolicy = IgnorePolicy.IGNORE_LANGUAGE_SPECIFIC_CHANGES
    settings.highlightPolicy = highlightPolicy
    return settings
  }

  private fun createDiffRequest(
    beforeFile: PsiFile,
    afterFile: PsiFile,
  ): SimpleDiffRequest {
    val beforeDiffContent = DocumentContentImpl(myFixture.project, beforeFile.fileDocument, PropertiesFileType.INSTANCE)
    val afterDiffContent = DocumentContentImpl(myFixture.project, afterFile.fileDocument, PropertiesFileType.INSTANCE)
    val request = SimpleDiffRequest("", beforeDiffContent, afterDiffContent, "", "")
    return request
  }
}