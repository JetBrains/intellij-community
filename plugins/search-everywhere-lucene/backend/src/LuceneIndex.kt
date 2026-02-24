package com.intellij.searchEverywhereLucene.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
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
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.div


class LuceneIndex(val project: Project, val coroutineScope: CoroutineScope, indexName: String, val LOG: Logger) : Disposable {

  private val indexPath: Path = let {
    project.getProjectDataPath("luceneIndex") / indexName
  }
  private val directory = FSDirectory.open(indexPath)
  private var writer: IndexWriter = createWriter()

  private var searcherManager: SearcherManager = SearcherManager(writer, SearcherFactory())

  //TODO implement some recovery logic when index creation fails.
  // This can happen when the project is reopened.
  //TODO implement operating in a read-only mode, that just hopes the other process will maintain the index properly. (Or even better, indicate some fallback flag so the fallback logic is used.)
  // Then it regularly checks if the index is still locked and once the lock can be aquired, we take ownership of the index and reindex everything once.
  private fun createWriter(): IndexWriter {
    val analyzer = StandardAnalyzer()
    val config = IndexWriterConfig(analyzer)
    // When closing the writer, the IDE shuts down. Since we reindex on startup anyways, we do not need to persist any pending changes.
    config.setCommitOnClose(false)

    return IndexWriter(directory, config)
  }


  /**
   * Executes a function that modifies an `IndexWriter` and ensures the changes are committed or rolled back in case of failure.
   * This method handles writer state management, including reopening the writer and refreshing the associated searcher.
   *
   *
   * @param changes A lambda function that takes an `IndexWriter` as an argument. It is not marked suspend, to ensure that indexing is not
   *                interrupted by coroutine context switches.
   */
  fun processChanges(changes: (IndexWriter) -> Unit) {
    val before = searcherManager.acquire().indexReader.numDocs()
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
    val after = searcherManager.acquire().indexReader.numDocs()
    LOG.debug{ "Lucene Index docs number changes: before=$before, after=${after} (diff ${after - before})" }
  }

  fun createIndex(initial_entities: List<Document>) {
    processChanges {
      writer.deleteAll()
      writer.addDocuments(initial_entities)
    }
  }

  fun addDocuments(entities: List<Document>) {
    processChanges {
      writer.addDocuments(entities)
    }
  }

  fun updateDocuments(updated_docs: List<Pair<Term, Document>>) {
    processChanges {
      updated_docs.forEach { (term, doc) -> writer.updateDocument(term, doc) }
    }
  }

  fun clearIndex() {
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

  override fun dispose() {
    try {
      searcherManager.close()
    }catch (_: IOException) {}
    try {
      writer.close()
    }catch (_: IOException) {}
    try {
      directory.close()
    }catch (_: IOException) {}
  }

  companion object {
    const val PAGE_SIZE: Int = 10
  }
}