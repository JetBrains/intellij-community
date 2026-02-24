package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.searchEverywhereLucene.backend.LuceneIndex
import com.intellij.searchEverywhereLucene.backend.SearchEverywhereLucenePluginDisposable
import com.intellij.searchEverywhereLucene.common.SearchEverywhereLuceneProviderIdUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.document.StringField
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.FuzzyQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class FileIndex(val project: Project, val coroutineScope: CoroutineScope) : Disposable {
  private val luceneIndex = LuceneIndex(project, coroutineScope, SearchEverywhereLuceneProviderIdUtils.LUCENE_FILES,LOG)
  private val scheduledIndexingOps = Channel<LuceneFileIndexOperation>(capacity = Channel.UNLIMITED)

  init {
    Disposer.register(SearchEverywhereLucenePluginDisposable.getInstance(project),this)
    Disposer.register(this, luceneIndex)

    coroutineScope.launch {
      // Wait until config is loaded and we can expect `ProjectFileIndex.getInstance()` to return the files to index.
      //Observation.awaitConfiguration(project)

      LOG.debug { "File Index in ${project.name} project stated processing changes..." }

      scheduledIndexingOps.consumeAsFlow().debounceBatch(1.seconds).collect { ops ->
        if (ops.size == 1) {
          processFileIndexOp(ops.first())
          return@collect
        }

        if (ops.any { it is LuceneFileIndexOperation.IndexAll }) {
          // If ANY one of the ops is a reindexing request, we can also drop all other updates, as the updated state will be picked up by the reindexing anyways.
          processFileIndexOp(LuceneFileIndexOperation.IndexAll)
        }
        else {
          // Since all others are ReindexFiles, we can merge them to reduce the number of times indexing runs:
          val merged_files = ops.asSequence()
            .filterIsInstance<LuceneFileIndexOperation.ReindexFiles>()
            .flatMap { it.changedFiles }
            .toSet()

          val merged_urls = ops.asSequence()
            .filterIsInstance<LuceneFileIndexOperation.ReindexFiles>()
            .flatMap { it.changedUrls }
            .toSet()

          processFileIndexOp(LuceneFileIndexOperation.ReindexFiles(merged_files, merged_urls))
        }
      }
    }
  }


  // TODO somehow inform UI that indexing is in progress
  private suspend fun processFileIndexOp(op: LuceneFileIndexOperation) {
    when (op) {
      LuceneFileIndexOperation.IndexAll -> {
        LOG.debug("Indexing all files")

        val fileIndex = ProjectFileIndex.getInstance(project)

        //TODO there can be duplicate files in here, but I dont think its a problem?
        val files = mutableListOf<VirtualFile>()

        readAction {
          fileIndex.iterateContent { file ->
            if (!file.isDirectory) {
              files.add(file)
            }
            true // continue iteration
          }
        }

        luceneIndex.processChanges { writer ->
          writer.deleteAll()
          files.forEach { file ->
            check(Files.exists(Paths.get(file.path))) { "The file at ${file.path} does not exist! We assume fileIndex.iterateContent only returns existing files" }
            check(file.isValid) { "The file at ${file.path} is not Valid! We assume fileIndex.iterateContent only returns valid files" }
            val (_, doc) = getDocument(file)
            writer.addDocument(doc)
          }
          LOG.debug {"Registered all ${files.size} files for the next lucene index commit." }
        }
      }

      is LuceneFileIndexOperation.ReindexFiles -> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val files_to_reindex = mutableListOf<VirtualFile>()
        val urls_to_delete = mutableListOf<Term>()
        
        
        // The reindexing Op may point to directories that should be reindexed, so we must reindex the contents of the dir, as these paths have changed.
        readAction {
          val virtualFiles = mutableListOf<VirtualFile>()
          virtualFiles.addAll(op.changedFiles)

          op.changedUrls.forEach { url ->
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl(url) ?: let {
              urls_to_delete.add(getTerm("$url"))
              return@forEach
            } 
            virtualFiles.add(virtualFile)
          }

          virtualFiles.forEach { virtualFile ->
            if (!fileIndex.isInProject(virtualFile)) return@forEach
            check(virtualFile.isValid) { "The file at ${virtualFile.presentableUrl} is not Valid! We assume file events only returns valid files" }
            if (!virtualFile.isDirectory) {
              files_to_reindex.add(virtualFile);
            } else {
              // Should be used from readAction
              fileIndex.iterateContentUnderDirectory(virtualFile) { file ->
                if (!file.isDirectory) {
                  files_to_reindex.add(file)
                }
                true // continue iteration
              }
            }
          }
        }

        if (files_to_reindex.isEmpty() && urls_to_delete.isEmpty()) return
        LOG.debug {"Reindexing ${files_to_reindex.size} files, deleting ${urls_to_delete.size} files" }

        luceneIndex.processChanges { writer ->
          files_to_reindex.forEach { file ->
            val (term, doc) = getDocument(file)
            writer.updateDocument(term, doc)
          }

          urls_to_delete.forEach { writer.deleteDocuments(it) }

          LOG.debug("Reindexed all updated files for the next lucene index commit.")
        }
      }
    }
  }

  fun scheduleIndexingOp(op: LuceneFileIndexOperation) {
    // Since the channel is unbounded, the sending must succeed.
    val r = scheduledIndexingOps.trySend(op)
    if (r.isFailure) {
      throw IllegalStateException("The channel failed to send, even though its unbounded!")
    }

  }


  fun buildQuery(params: SeParams): Query {
    val fixedPattern = params.inputQuery.trim().lowercase()
    val prefixQuery = BoostQuery(PrefixQuery(Term(FILE_LOWERCASE_NAME, fixedPattern)), 5f)
    val fuzzyQuery = FuzzyQuery(Term(FILE_LOWERCASE_NAME, fixedPattern))

    val builder = BooleanQuery.Builder()
    builder.add(prefixQuery, BooleanClause.Occur.SHOULD)
    builder.add(fuzzyQuery, BooleanClause.Occur.SHOULD)
    return builder.build()
  }

  fun search(params: SeParams): Flow<LuceneFileSearchResult> {
    val query = buildQuery(params)
    val deletedFilesToRemoveFromIndex = mutableSetOf<String>()
    return luceneIndex.search(query).mapNotNull { (scoreDoc, doc) ->
      //LOG.debug { "Search \"${params.inputQuery}\" returned $doc with score ${scoreDoc.score}" }
      val url = doc.get(FILE_URL)
      val virtualFile = VirtualFileManager.getInstance().findFileByUrl(url) ?: let {
        deletedFilesToRemoveFromIndex.add(url)
        return@mapNotNull null
      }
      LuceneFileSearchResult(virtualFile, scoreDoc.score)
    }.onCompletion {
      //This will fire often, as each character typed by the user causes a new search.
      //And since the same deleted files are likely showing up repeatedly, there are a bunch of requests to delete the same file.
      //We could track the deleted files in the FilesProvider instead, but this would make the FileIndex interface more complex.
      //The debouncing/merging logic in place should be enough to handle this anyway.
      if (deletedFilesToRemoveFromIndex.isNotEmpty()) {
        LOG.debug { "Scheduling deletion of ${deletedFilesToRemoveFromIndex.size} files from index: ${deletedFilesToRemoveFromIndex.toString()}" }
        scheduleIndexingOp(LuceneFileIndexOperation.ReindexFiles(changedUrls = deletedFilesToRemoveFromIndex))
      }
    }
  }

  override fun dispose() {}


  companion object {
    fun getInstance(project: Project): FileIndex = project.service()
    val LOG: Logger = logger<FileIndex>()
    const val FILE_NAME: String = "fileName"
    const val FILE_LOWERCASE_NAME: String = "fileLowercaseName"

    const val FILE_URL: String = "uri"


    @Throws(IOException::class)
    fun getDocument(virtualFile: VirtualFile): Pair<Term, Document> {
      val document = Document()

      val tokenizedField = FieldType()
      tokenizedField.setStored(true)
      tokenizedField.setTokenized(true)
      tokenizedField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)

      // TODO store filename and virtualFile.getPresentablePath(), use the virtualFile.getURL() as the identifier.
      val fields = listOf(
        //Field(FILE_SOURCE_ROOT_PATH, fileInfo.sourceRootPath, tokenizedField),
        //StringField(FILE_CONTENT_ROOT_PATH, fileInfo.contentRootPath, Field.Store.YES),
        StringField(FILE_URL, virtualFile.url, Field.Store.YES),
        StringField(FILE_NAME, virtualFile.nameWithoutExtension, Field.Store.NO),
        StringField(FILE_LOWERCASE_NAME, virtualFile.nameWithoutExtension.lowercase(), Field.Store.NO)
      )

      fields.forEach { document.add(it) }

      val term = getTerm(virtualFile.url)

      return Pair(term, document)
    }

    private fun getTerm(url: String): Term {
      val term = Term(FILE_URL, url)
      return term
    }
  }
}

sealed class LuceneFileIndexOperation {
  data object IndexAll : LuceneFileIndexOperation()
  data class ReindexFiles(val changedFiles: Set<VirtualFile> = emptySet(), val changedUrls: Set<String> = emptySet()) : LuceneFileIndexOperation()
}


@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.debounceBatch(
  timeout: Duration,
  maxSize: Int = Int.MAX_VALUE,
): Flow<List<T>> = channelFlow {
  val ch: ReceiveChannel<T> = this@debounceBatch.produceIn(this)

  val batch = ArrayList<T>()

  suspend fun flush() {
    if (batch.isNotEmpty()) {
      send(batch.toList())
      batch.clear()
    }
  }

  while (true) {
    val got = select<Boolean> {
      ch.onReceiveCatching { result ->
        val v = result.getOrNull()
        if (v == null) {
          // upstream completed
          false
        }
        else {
          batch.add(v)
          if (batch.size >= maxSize) flush()
          true
        }
      }

      onTimeout(timeout) {
        flush()
        true
      }
    }

    if (!got) break
  }

  flush()
}