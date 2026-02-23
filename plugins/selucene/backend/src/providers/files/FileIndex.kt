package com.intellij.selucene.backend.providers.files

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.selucene.backend.LuceneIndex
import com.intellij.selucene.common.SeLuceneProviderIdUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
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
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class FileIndex(val project: Project, val coroutineScope: CoroutineScope) {
  private val luceneIndex = LuceneIndex(project, coroutineScope, SeLuceneProviderIdUtils.LUCENE_FILES)
  private val scheduledIndexingOps = Channel<LuceneFileIndexOperation>(capacity = Channel.UNLIMITED)

  init {
    DumbService.getInstance(project).runWhenSmart {
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
              .flatMap { it.addedFiles }
              .toList()

            processFileIndexOp(LuceneFileIndexOperation.ReindexFiles(merged_files))
          }
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
        luceneIndex.processChanges { writer ->
          writer.deleteAll()
          fileIndex.iterateContent { file ->
            if (!file.isDirectory) {

              check(Files.exists(Paths.get(file.path))) { "The file at ${file.path} does not exist! We assume fileIndex.iterateContent only returns existing files" }
              check(file.isValid) { "The file at ${file.path} is not Valid! We assume fileIndex.iterateContent only returns valid files" }
              val (_,doc) = getDocument(file)
              writer.addDocument(doc)
            }

            true // continue iteration
          }
          LOG.debug("Registered all files for the next lucene index commit.")
        }
      }

      is LuceneFileIndexOperation.ReindexFiles -> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val files = readAction { op.addedFiles.filter { fileIndex.isInProject(it) } }

        if (files.isEmpty()) return

        LOG.debug("Reindexing files:")


        luceneIndex.processChanges { writer ->
          files.forEach { file ->

            check(Files.exists(Paths.get(file.path))) { "The file at ${file.path} does not exist! We assume file events only returns existing files" }
            check(file.isValid) { "The file at ${file.path} is not Valid! We assume file events only returns valid files" }
            val (term, doc) = getDocument(file)
            LOG.debug { "Updating $term to $doc" }
            writer.updateDocument(term, doc)
          }

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

  // TODO figure out why old files no longer show up. They ARE returned from this search function after all.
  //   SeLuceneFilesProvider performs refiltering: val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of((it.path))) ?: return@collect
  // TODO Store virtual file ID in the index to allow efficient retrieval later.
  fun search(params: SeParams): Flow<LuceneFileSearchResult> {
    val query = buildQuery(params)
    LOG.debug { "Search for ${params.inputQuery} with ${params.filter} was translated into Lucene Query: $query" }
    return luceneIndex.search(query).map { (scoreDoc, doc) ->
      //LOG.debug { "Search \"${params.inputQuery}\" returned $doc with score ${scoreDoc.score}" }
      val name = doc.get(FILE_NAME)
      val path = doc.get(FILE_ABSOLUTE_PATH)
      LuceneFileSearchResult(name, path, scoreDoc.score)
    }
  }

  companion object {
    fun getInstance(project: Project): FileIndex = project.service()
    val LOG: Logger = logger<FileIndex>()
    const val FILE_NAME: String = "fileName"
    const val FILE_LOWERCASE_NAME: String = "fileLowercaseName"
    const val FILE_CONTENT_ROOT_PATH: String = "fileContentRootPath"
    const val FILE_SOURCE_ROOT_PATH: String = "fileSourceRootPath"
    const val FILE_ABSOLUTE_PATH: String = "fileAbsolutePath"


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
        StringField(FILE_ABSOLUTE_PATH, virtualFile.path, Field.Store.YES),
        StringField(FILE_NAME, virtualFile.nameWithoutExtension, Field.Store.YES),
        StringField(FILE_LOWERCASE_NAME, virtualFile.nameWithoutExtension.lowercase(), Field.Store.YES)
      )

      fields.forEach { document.add(it) }

      val term = Term(FILE_ABSOLUTE_PATH, virtualFile.path)

      return Pair(term, document)
    }
  }
}

sealed class LuceneFileIndexOperation {
  data object IndexAll : LuceneFileIndexOperation()

  //TODO how to represent deleted files?
  data class ReindexFiles(val addedFiles: List<VirtualFile>) : LuceneFileIndexOperation()
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