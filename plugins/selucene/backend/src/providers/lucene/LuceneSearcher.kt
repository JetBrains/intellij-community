// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.selucene.backend.providers.lucene

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path


class LuceneSearcher(indexPath: Path) {
  private val indexSearcher: IndexSearcher

  init {
    val indexDirectory = FSDirectory.open(indexPath)
    val directoryReader = DirectoryReader.open(indexDirectory)
    indexSearcher = IndexSearcher(directoryReader)
  }

  private fun searchAfter(after: ScoreDoc?, query: Query, pageSize: Int): TopDocs {
    return indexSearcher.searchAfter(after, query, pageSize)
  }

  private fun getDocument(docId: Int): Document {
    return indexSearcher.storedFields().document(docId)
  }

  private fun close() {
    indexSearcher.indexReader.close()
  }

  companion object {
    fun create(project: Project): LuceneSearcher? = LuceneIndexer.getInstance(project).indexPath?.let {
      LuceneSearcher(it)
    }

    fun search(pattern: String, project: Project): Flow<LuceneFileSearchResult> {
      val fixedPattern = pattern.trim().lowercase()
      val prefixQuery = BoostQuery(PrefixQuery(Term(LuceneIndexer.FILE_LOWERCASE_NAME, fixedPattern)), 5f)
      val fuzzyQuery = FuzzyQuery(Term(LuceneIndexer.FILE_LOWERCASE_NAME, fixedPattern))

      val builder = BooleanQuery.Builder()
      builder.add(prefixQuery, BooleanClause.Occur.SHOULD)
      builder.add(fuzzyQuery, BooleanClause.Occur.SHOULD)

      return search(builder.build(), project)
    }

    private fun search(query: Query, project: Project): Flow<LuceneFileSearchResult> {
      val fuzzySearcher =
        try {
          LuceneIndexer.getInstance(project).indexPath?.let {
            LuceneSearcher(it)
          }
        }
        catch (e: IndexNotFoundException) {
          LOG.warn("Lucene search failed: ${e}")
          return emptyFlow()
        } ?: run {
          LOG.warn("Lucene search failed: no index found")
          return emptyFlow()
        }

      return channelFlow {
        try {
          var after: ScoreDoc? = null
          var hits: TopDocs? = null

          while (hits == null || hits.scoreDocs.isNotEmpty()) {
            ensureActive()

            hits = fuzzySearcher.searchAfter(after, query, PAGE_SIZE)
            if (hits.scoreDocs.isEmpty()) break
            after = hits.scoreDocs.last()

            for (scoreDoc: ScoreDoc in hits.scoreDocs) {
              val doc: Document = fuzzySearcher.getDocument(scoreDoc.doc)
              val name = doc.get(LuceneIndexer.FILE_NAME)
              val path = doc.get(LuceneIndexer.FILE_ABSOLUTE_PATH)
              val scoredText = LuceneFileSearchResult(name, path, scoreDoc.score)

              send(scoredText)
            }
          }
        }
        finally {
          fuzzySearcher.close()
        }
      }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
    }

    val LOG: Logger = logger<LuceneSearcher>()
    private const val PAGE_SIZE: Int = 10
  }
}