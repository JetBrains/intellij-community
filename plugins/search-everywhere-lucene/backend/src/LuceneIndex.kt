package com.intellij.searchEverywhereLucene.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import java.io.IOException
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.div


@OptIn(ExperimentalAtomicApi::class)
class LuceneIndex(val project: Project, indexName: String, val log: Logger) : Disposable {

  // These are managed as one to ensure proper cleanup and synchronization.
  private data class IndexReaderWriter(val writer: IndexWriter, val searcherManager: SearcherManager)

  private val indexPath: Path = let {
    project.getProjectDataPath("luceneIndex") / indexName
  }
  private val directory = FSDirectory.open(indexPath)
  private var atomicIndexRW: AtomicReference<IndexReaderWriter> = AtomicReference(createIndexReaderWriter())

  //TODO implement some recovery logic when index creation fails.
  // This can happen when the project is reopened.
  //TODO implement operating in a read-only mode, that just hopes the other process will maintain the index properly. (Or even better, indicate some fallback flag so the fallback logic is used.)
  // Then it regularly checks if the index is still locked and once the lock can be acquired, we take ownership of the index and reindex everything once.
  private fun createIndexReaderWriter(): IndexReaderWriter {
    val analyzer = StandardAnalyzer()
    val config = IndexWriterConfig(analyzer)
    // When closing the writer, the IDE shuts down. Since we reindex on startup anyway, we do not need to persist any pending changes.
    config.setCommitOnClose(false)

    val writer = IndexWriter(directory, config)
    val searcherManager = SearcherManager(writer, SearcherFactory())
    return IndexReaderWriter(writer, searcherManager)
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
    val indexRW = atomicIndexRW.load()
    try {
      var before = 0
      if (log.isDebugEnabled) {
        val searcher = indexRW.searcherManager.acquire()
        before = searcher.indexReader.numDocs()
        indexRW.searcherManager.release(searcher)
      }

      changes(indexRW.writer)
      indexRW.writer.commit()
      indexRW.searcherManager.maybeRefresh()
      if (log.isDebugEnabled) {
        val searcher = indexRW.searcherManager.acquire()
        val after = searcher.indexReader.numDocs()
        indexRW.searcherManager.release(searcher)
        
        log.debug{ "Lucene Index docs number changes: before=$before, after=${after} (diff ${after - before})" }
      }
    }
    catch (t: Throwable) {
      // Best-effort rollback of any uncommitted changes
      try {
        indexRW.searcherManager.close()
      }
      catch (e: Throwable) {
        t.addSuppressed(e)
      }

      // This also closes the writer.
      try {
        indexRW.writer.rollback()
      }
      catch (e: Throwable) {
        t.addSuppressed(e)
      }
      // Reopen writer + searcher infrastructure so the index can continue operating
      atomicIndexRW.store(createIndexReaderWriter())

      log.error(LuceneIndexInsufficientFaultHandlingException(t))
    }
  }

  fun search(query: Query): Flow<Pair<ScoreDoc, Document>> {
    return channelFlow {
      val searcherManager = atomicIndexRW.load().searcherManager
      val searcher: IndexSearcher = searcherManager.acquire()
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
        // Docs say it's safe to call this after close, so even if atomicIndexRW is replaced with another instance, this is okay.
        searcherManager.release(searcher)
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  override fun dispose() {
    val indexRW = this.atomicIndexRW.load()

    var exception: Throwable? = null

    try {
      indexRW.searcherManager.close()
    }
    catch (e: Throwable) {
      exception = e
    }

    try {
      indexRW.writer.close()
    }
    catch (e: Throwable) {
      if (exception == null) {
        exception = e
      }
      else {
        exception.addSuppressed(e)
      }
    }

    try {
      directory.close()
    }
    catch (e: Throwable) {
      if (exception == null) {
        exception = e
      }
      else {
        exception.addSuppressed(e)
      }
    }

    if (exception != null) {
      log.error(LuceneIndexInsufficientFaultHandlingException(exception))
    }
  }


  companion object {
    const val PAGE_SIZE: Int = 10
  }
}

class LuceneIndexInsufficientFaultHandlingException(cause: Throwable) : RuntimeException(cause)