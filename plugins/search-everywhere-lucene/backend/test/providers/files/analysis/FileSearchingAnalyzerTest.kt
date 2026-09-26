package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import com.intellij.searchEverywhereLucene.backend.AnalyzersTestBase
import org.junit.jupiter.api.Test

/**
 * Tests for the FileSearchAnalyzer used for query analysis during search.
 */
class FileSearchingAnalyzerTest : AnalyzersTestBase() {

  @Test
  fun testFileSearchAnalyzer() {
    tokenizing(FileSearchAnalyzer(), "SearchEveryWhereUI.java")
      .print()
      .producesToken("SearchEveryWhereUI.java", FileTokenType.PATH)
      .producesToken("java", FileTokenType.FILETYPE, 19, 23)
      .producesToken("searcheverywhereui", FileTokenType.FILENAME, 0, 18)
      .producesToken("search", FileTokenType.FILENAME_PART, 0, 6)
      .producesToken("every", FileTokenType.FILENAME_PART, 6, 11)
      .producesToken("sewui", FileTokenType.FILENAME_ABBREVIATION, 0, 18)
      .producesToken("u", FileTokenType.FILENAME_PART, 16, 17)
      .producesToken("i", FileTokenType.FILENAME_PART, 17, 18)
      .noDuplicateTokens()

    tokenizing(FileSearchAnalyzer(), "com/intellij/MyFile.kt")
      .producesToken("kt", FileTokenType.FILETYPE)
      .producesToken("com/intellij/MyFile.kt", FileTokenType.PATH)
      .producesToken("com", FileTokenType.PATH_SEGMENT)
      .producesToken("intellij", FileTokenType.PATH_SEGMENT)
      .producesToken("MyFile.kt", FileTokenType.PATH_SEGMENT)
      .producesToken("myfile", FileTokenType.FILENAME)
      .producesToken("my", FileTokenType.FILENAME_PART)
      .producesToken("file", FileTokenType.FILENAME_PART)
      .print()
      .noDuplicateTokens()
  }

  @Test
  fun `test FileSearchAnalyzer hidden files`() {
    tokenizing(FileSearchAnalyzer(), ".gitignore")
      .print()
      .producesToken(".gitignore", FileTokenType.PATH)
      .producesToken("gitignore", FileTokenType.FILETYPE)
      .noDuplicateTokens()

    tokenizing(FileSearchAnalyzer(), ".git/test")
      .print()
      .producesToken("test", FileTokenType.FILENAME)
      .producesToken(".git/test", FileTokenType.PATH)

    tokenizing(FileSearchAnalyzer(), ".hidden").print()
      .producesToken("hidden", FileTokenType.FILETYPE)
      .producesToken(".hidden", FileTokenType.PATH)
  }

  @Test
  fun `test FileSearchAnalyzer incomplete`() {
    tokenizing(FileSearchAnalyzer(), "Rea")
      .print()
      .producesToken("rea", FileTokenType.FILENAME_PART)
      .noDuplicateTokens()

    tokenizing(FileSearchAnalyzer(), "java")
      .print()
      .producesToken("java", FileTokenType.FILENAME_PART)
      .producesToken("java", FileTokenType.FILETYPE)
      .producesToken("java", FileTokenType.FILENAME)
      .producesToken("java", FileTokenType.PATH_SEGMENT)
      .noDuplicateTokens()

    tokenizing(FileSearchAnalyzer(), "kt")
      .print()
      .producesToken("kt", FileTokenType.FILENAME_PART)
      .producesToken("kt", FileTokenType.FILETYPE)
      .producesToken("kt", FileTokenType.FILENAME)
      .producesToken("kt", FileTokenType.PATH_SEGMENT)
      .noDuplicateTokens()

    tokenizing(FileSearchAnalyzer(), "SearchEver")
      .print()
      .producesToken("searchever", FileTokenType.FILENAME, 0, 10)
      .producesToken("search", FileTokenType.FILENAME_PART, 0, 6)
      .producesToken("ever", FileTokenType.FILENAME_PART, 6, 10)
      .producesToken("se", FileTokenType.FILENAME_ABBREVIATION, 0, 10)
      .noDuplicateTokens()
  }


  @Test
  fun `test FileSearchAnalyzer with Spaces`() {
    tokenizing(FileSearchAnalyzer(), "SearchEveryWhereUI.java com/intellij/Test.txt")
      .print()
      .producesToken("SearchEveryWhereUI.java", FileTokenType.PATH)
      .producesToken("java", FileTokenType.FILETYPE, 19, 23)
      .producesToken("search", FileTokenType.FILENAME_PART, 0, 6)
      .producesToken("every", FileTokenType.FILENAME_PART, 6, 11)
      .producesToken("sewui", FileTokenType.FILENAME_ABBREVIATION, 0, 18)
      .producesToken("u", FileTokenType.FILENAME_PART, 16, 17)
      .producesToken("i", FileTokenType.FILENAME_PART, 17, 18)
      .producesToken("com/intellij/Test.txt", FileTokenType.PATH)
      .producesToken("com", FileTokenType.PATH_SEGMENT)
      .producesToken("intellij", FileTokenType.PATH_SEGMENT)
      .producesToken("Test.txt", FileTokenType.PATH_SEGMENT)
      .producesToken("test", FileTokenType.FILENAME)
      .producesToken("txt", FileTokenType.FILETYPE)
      .noDuplicateTokens()
  }

  @Test
  fun `test FileSearchAnalyzer word index`() {
    tokenizing(FileSearchAnalyzer(), "Readme foo")
      .print()
      .producesTokenWithWordIndex("readme", FileTokenType.FILENAME, 0)
      .producesTokenWithWordIndex("foo", FileTokenType.FILENAME, 1)
  }

  @Test
  fun `produces filenameAbbreviation tokens`() {
    tokenizing(FileSearchAnalyzer(), "sec")
      .print()
      .producesToken("sec", FileTokenType.FILENAME_ABBREVIATION)
  }

  @Test
  fun `produces NO filenameAbbreviation with Skip tokens`() {
    tokenizing(FileSearchAnalyzer(), "sec")
      .print()
      .producesNoTokenThat { it.types.contains(FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS) }

  }


  @Test
  fun `IJPL-220105 ngrams filename parts`() {
    tokenizing(FileSearchAnalyzer(), "clag")
      .print()
      .producesToken("cl", FileTokenType.FILENAME_PART, 0, 2)
      .producesToken("cl", FileTokenType.PATH_SEGMENT_PREFIX, 0, 2)
      .producesToken("la", FileTokenType.FILENAME_PART, 1, 3)
      .producesToken("la", FileTokenType.PATH_SEGMENT_PREFIX, 1, 3)
      .producesToken("ag", FileTokenType.FILENAME_PART, 2, 4)
      .producesToken("ag", FileTokenType.PATH_SEGMENT_PREFIX, 2, 4)
      .producesToken("cla", FileTokenType.FILENAME_PART, 0, 3)
      .producesToken("cla", FileTokenType.PATH_SEGMENT_PREFIX, 0, 3)
      .producesToken("lag", FileTokenType.FILENAME_PART, 1, 4)
      .producesToken("lag", FileTokenType.PATH_SEGMENT_PREFIX, 1, 4)
  }

  @Test
  fun `IJPL-240179 ngrams generated for multi-part filenames`() {
    // "sea" and "se" should be generated from the "search" part of SearchEveryWhereUI,
    // enabling queries like "sean" to find SearchAnalyzer via prefix matching.
    tokenizing(FileSearchAnalyzer(), "SearchEveryWhereUI")
      .print()
      .producesToken("se", FileTokenType.FILENAME_PART, 0, 2)
      .producesToken("se", FileTokenType.PATH_SEGMENT_PREFIX, 0, 2)
      .producesToken("sea", FileTokenType.FILENAME_PART, 0, 3)
      .producesToken("sea", FileTokenType.PATH_SEGMENT_PREFIX, 0, 3)
  }

}
