package com.intellij.selucene.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import kotlin.io.path.div


class LuceneIndex(val project: Project, val coroutineScope: CoroutineScope, indexName: String) {
  private val indexPath: Path = let {
    project.getProjectDataPath("luceneIndex") / indexName
  }
  private val directory = FSDirectory.open(indexPath)
  private var writer: IndexWriter = createWriter()

  //TODO use a separate background thread, that periodically calls ReferenceManager.maybeRefresh()
  private var searcherManager: SearcherManager = SearcherManager(writer, SearcherFactory())

  private fun createWriter(): IndexWriter {
    val analyzer = StandardAnalyzer()
    val config = IndexWriterConfig(analyzer)
    return IndexWriter(directory, config)
  }

  fun processChanges(changes: (IndexWriter) -> Unit) {
    try {
      changes(writer)
      writer.commit()
    }
    catch (t: Throwable) {
      // Best-effort rollback of any uncommitted changes

      writer.rollback()
      writer.close()

      // Reopen writer + searcher infrastructure so the index can continue operating
      writer = createWriter()
      searcherManager = SearcherManager(writer, SearcherFactory())

      throw t
    }
    searcherManager.maybeRefresh()
  }

  suspend fun createIndex(initial_entities: List<Document>) {
    processChanges {
      writer.deleteAll()
      writer.addDocuments(initial_entities)
    }
  }

  suspend fun addDocuments(entities: List<Document>) {
    processChanges {
      writer.addDocuments(entities)
    }
  }

  suspend fun updateDocuments(updated_docs: List<Pair<Term, Document>>) {
    processChanges {
      updated_docs.forEach { (term, doc) -> writer.updateDocument(term, doc) }
    }
  }

  suspend fun clearIndex() {
    processChanges { writer.deleteAll() }
  }


  fun search(query: Query): Flow<Pair<ScoreDoc, Document>> {
    val searcher: IndexSearcher = searcherManager.acquire()

    return channelFlow {
      try {
        var after: ScoreDoc? = null
        var hits: TopDocs? = null

        while (hits == null || hits.scoreDocs.isNotEmpty()) {
          ensureActive()

          hits = searcher.searchAfter(after, query, PAGE_SIZE)
          if (hits.scoreDocs.isEmpty()) break
          after = hits.scoreDocs.last()

          for (scoreDoc: ScoreDoc in hits.scoreDocs) {
            val doc: Document = searcher.storedFields().document(scoreDoc.doc)
            send(Pair(scoreDoc, doc))
          }
        }
      }
      finally {
        searcherManager.release(searcher)
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  companion object {
    const val PAGE_SIZE: Int = 10
  }
}