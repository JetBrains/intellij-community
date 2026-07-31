// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereLucene.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.apache.lucene.document.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestApplication
class LuceneIndexCompatibilityTest : LuceneIndexTestBase() {
  override val log: Logger = logger<LuceneIndexCompatibilityTest>()

  @Test
  fun `cache from a previous codec is ignored`() {
    val indexName = "legacy-index-${UUID.randomUUID()}"
    val legacyIndexPath = project.getProjectDataPath("luceneIndex").resolve(indexName)
    legacyIndexPath.createDirectories()
    legacyIndexPath.resolve("segments_1").writeText("invalid legacy index")

    val index = LuceneIndex(project, indexName, log)
    Disposer.register(projectModel.disposableRule.disposable, index)

    runBlocking {
      index.processChanges { writer ->
        writer.addDocument(Document())
      }
    }

    assertEquals(1, index.withSearcher { it.indexReader.numDocs() })
  }
}
