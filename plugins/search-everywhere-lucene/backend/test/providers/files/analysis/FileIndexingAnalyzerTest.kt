package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import com.intellij.searchEverywhereLucene.backend.AnalyzersTestBase
import org.junit.jupiter.api.Test

/**
 * Tests for the individual indexing analyzers used to build per-type document fields.
 */
class FileIndexingAnalyzerTest : AnalyzersTestBase() {

  @Test
  fun `test FileNameAnalyzer`() {
    tokenizing(FileNameAnalyzer(), "SearchEveryWhereUI.java")
      .print()
      .noDuplicateTokens()
      .producesToken("SearchEveryWhereUI.java", FileTokenType.PATH)
      .producesToken("java", FileTokenType.FILETYPE, 19, 23)
      .producesToken("searcheverywhereui", FileTokenType.FILENAME, 0, 18)
      .producesToken("search", FileTokenType.FILENAME_PART, 0, 6)
      .producesToken("every", FileTokenType.FILENAME_PART, 6, 11)
      .producesToken("where", FileTokenType.FILENAME_PART, 11, 16)
      .producesToken("sewui", FileTokenType.FILENAME_ABBREVIATION, 0, 18)
      .producesToken("u", FileTokenType.FILENAME_PART, 16, 17)
      .producesToken("i", FileTokenType.FILENAME_PART, 17, 18)
      .noDuplicateTokens()
  }

  @Test
  fun `test FileNameAnalyzer emits skip abbreviations`() {
    // SearchEveryWhereUI has 5 camel parts (Search, Every, Where, U, I); with allowedSkip=1 the filter emits skip variants
    tokenizing(FileNameAnalyzer(), "SearchEveryWhereUI.java")
      .producesToken("sewu", FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS, 0, 18)
      .producesToken("sewi", FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS, 0, 18)
      .producesToken("seui", FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS, 0, 18)
      .producesToken("swui", FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS, 0, 18)
  }

  @Test
  fun `segments of the filepath are emitted`() {
    tokenizing(FilePathAnalyzer(), "foo/bar/Readme.md")
      .print()
      .producesToken("foo", FileTokenType.PATH_SEGMENT)
      .producesToken("bar", FileTokenType.PATH_SEGMENT)
      .producesToken("Readme.md", FileTokenType.PATH_SEGMENT)

    tokenizing(FilePathAnalyzer(), "test/providers/files/FileIndexingAnalyzerTest.kt")
      .print()
      .producesToken("test", FileTokenType.PATH_SEGMENT)
      .producesToken("providers", FileTokenType.PATH_SEGMENT)
      .producesToken("files", FileTokenType.PATH_SEGMENT)
      .producesToken("FileIndexingAnalyzerTest.kt", FileTokenType.PATH_SEGMENT)
  }

  @Test
  fun `filetypes are emitted as tokens`() {
    tokenizing(FileTypeAnalyzer(), "java")
      .print()
      .producesToken("java", FileTokenType.FILETYPE)

    tokenizing(FileTypeAnalyzer(), "md")
      .print()
      .producesToken("md", FileTokenType.FILETYPE)

    tokenizing(FileTypeAnalyzer(), "kt")
      .print()
      .producesToken("kt", FileTokenType.FILETYPE)

    tokenizing(FileTypeAnalyzer(), "gitignore")
      .print()
      .producesToken("gitignore", FileTokenType.FILETYPE)
  }
}
