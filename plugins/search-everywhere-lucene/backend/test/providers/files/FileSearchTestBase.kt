package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.searchEverywhereLucene.backend.LuceneIndexTestBase
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.FileSearchAnalyzer
import com.intellij.searchEverywhereLucene.backend.util.TokenTypeFilteringAnalyzer
import kotlinx.coroutines.runBlocking
import org.apache.lucene.document.Document
import org.apache.lucene.search.Query

abstract class FileSearchTestBase : LuceneIndexTestBase() {

  override val log: Logger = logger<FileSearchTest>()

  override fun buildSimpleQuery(pattern: String): Query {
    return FileIndex.buildQuery(SeParams(pattern, SeFilterState.Empty))
  }

  fun buildQueryOnlyUsingTokenTypes(input: String, vararg tokenTypes: String): Query {
    val analyzer = TokenTypeFilteringAnalyzer(FileSearchAnalyzer(), tokenTypes.toList())
    return FileIndex.buildQuery(SeParams(input, SeFilterState.Empty), analyzer)
  }

  override val isEquivalent: (Document, Document) -> Boolean = { d1, d2 ->
    d1.get(FileIndex.FILE_URL) == d2.get(FileIndex.FILE_URL)
  }

  fun buildMockVirtualFile(path: String): MockVirtualFile {
    val segments = path.split('/')
    val file = MockVirtualFile(segments.last())
    var current: MockVirtualFile = file
    for (segment in segments.dropLast(1).reversed()) {
      val parent = MockVirtualFile(true, segment)
      parent.addChild(current)
      current = parent
    }
    return file
  }

  fun file(path: String): Document {
    return runBlocking {
      val fileIndex = FileIndex.getInstance(project)
      val fileData = readAction { fileIndex.getFileData(buildMockVirtualFile(path)) }
      fileIndex.buildDocument(fileData).second
    }
  }
}