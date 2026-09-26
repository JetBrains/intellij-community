package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.searchEverywhereLucene.backend.LuceneIndex
import com.intellij.searchEverywhereLucene.backend.SearchEverywhereLuceneBackendBundle
import com.intellij.searchEverywhereLucene.backend.SearchEverywhereLucenePluginDisposable
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.FileNameAnalyzer
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.FilePathAnalyzer
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.FileSearchAnalyzer
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.FileTokenType
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.MultiTypeAttribute
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.WordAttribute
import com.intellij.searchEverywhereLucene.common.SearchEverywhereLuceneProviderIdUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.ConstantScoreQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
class FileIndex(val project: Project, coroutineScope: CoroutineScope) : Disposable {
  private val indexingEnabled = isIndexingEnabled()
  private val luceneIndex by lazy(LazyThreadSafetyMode.NONE) {
    LuceneIndex(project, SearchEverywhereLuceneProviderIdUtils.LUCENE_FILES, LOG)
  }
  private val scheduledIndexingOps = Channel<LuceneFileIndexOperation>(capacity = Channel.UNLIMITED)
  val initialIndexingCompleted: CompletableDeferred<Unit> = CompletableDeferred()

  init {
    Disposer.register(SearchEverywhereLucenePluginDisposable.getInstance(project), this)
    if (!indexingEnabled) {
      LOG.info("Lucene file indexing is disabled by registry key $LUCENE_INDEX_ENABLED_REGISTRY_KEY.")
    }
    else {
      Disposer.register(this, luceneIndex)

      coroutineScope.launch {
        // Wait until the config is loaded, and we can expect `ProjectFileIndex.getInstance()` to return the files to index.

        LOG.debug { "File Index in ${project.name} project stated processing changes..." }

        scheduledIndexingOps.consumeAsFlow().debounceBatch(1.seconds).collect { ops ->
          if (ops.size == 1) {
            processFileIndexOp(ops.first())
            return@collect
          }

          if (ops.any { it is LuceneFileIndexOperation.IndexAll }) {
            // If ANY one of the ops is a reindexing request, we can also drop all other updates, as reindexing will pick up the updated state anyway.
            processFileIndexOp(LuceneFileIndexOperation.IndexAll)
          }
          else {
            // Since all others are ReindexFiles, we can merge them to reduce the number of times indexing runs:
            val mergedFiles = ops.asSequence()
              .filterIsInstance<LuceneFileIndexOperation.ReindexFiles>()
              .flatMap { it.changedFiles }
              .toSet()

            val mergedUrls = ops.asSequence()
              .filterIsInstance<LuceneFileIndexOperation.ReindexFiles>()
              .flatMap { it.changedUrls }
              .toSet()

            processFileIndexOp(LuceneFileIndexOperation.ReindexFiles(mergedFiles, mergedUrls))
          }
        }
      }
    }
  }

  @Suppress("unused")
  @TestOnly
  suspend fun awaitIndexCreation() {
    processFileIndexOp(LuceneFileIndexOperation.IndexAll)
  }

  private suspend fun processFileIndexOp(op: LuceneFileIndexOperation) {
    runCatching {
      coroutineScope {
        val task = async { doProcessFileIndexOp(op) }
        val progressJob = launch {
          delay(PROGRESS_DISPLAY_DELAY)
          if (task.isActive) {
            withBackgroundProgress(
              project,
              SearchEverywhereLuceneBackendBundle.message("searchEverywhereLucene.files.indexing.progress"),
              TaskCancellation.nonCancellable(),
            ) {
              task.await()
            }
          }
        }

        try {
          task.await()
        }
        finally {
          progressJob.cancel()
        }
      }
    }.getOrLogException(LOG)
  }

  private suspend fun doProcessFileIndexOp(op: LuceneFileIndexOperation) {
    when (op) {
      LuceneFileIndexOperation.IndexAll -> {
        LOG.debug("Indexing all files")
        val currentTime = System.currentTimeMillis()

        val fileIndex = ProjectFileIndex.getInstance(project)

        // Collect lightweight VirtualFile references only; document creation happens per batch.
        val files = mutableListOf<VirtualFile>()
        fileIndex.iterateContent { file ->
          if (file.isValid) {
            files.add(file)
          }
          true // continue iteration
        }

        luceneIndex.processChanges { writer ->
          writer.deleteAll()
          for (batch in files.chunked(INDEX_BATCH_SIZE)) {
            val fileDataList = readAction { batch.asIterable().filter { it.isValid }.map { getFileData(it) }.toList() }
            val docs = fileDataList.asIterable().map { buildDocument(it).second }
            writer.addDocuments(docs)
          }
        }
        LOG.debug { "Indexed all ${files.size} files in ${System.currentTimeMillis() - currentTime} ms" }
        initialIndexingCompleted.complete(Unit)
      }

      is LuceneFileIndexOperation.ReindexFiles -> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val filesToReindex = mutableListOf<VirtualFile>()
        val urlsToDelete = mutableListOf<Term>()


        // The reindexing Op may point to directories that should be reindexed, so we must reindex the contents of the dir, as these paths have changed.
        readAction {
          val virtualFiles = mutableListOf<VirtualFile>()
          virtualFiles.addAll(op.changedFiles)

          op.changedUrls.forEach { url ->
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl(url) ?: let {
              urlsToDelete.add(getPrimaryKeyTerm(url))
              return@forEach
            }
            virtualFiles.add(virtualFile)
          }

          virtualFiles.forEach { virtualFile ->
            if (!fileIndex.isInProject(virtualFile)) return@forEach

            if (!virtualFile.isValid) {
              LOG.info("Skipping indexing ${virtualFile.url}, because it is not valid. Scheduling for deletion instead. We assume the files scheduled for reindex are valid files.")
              urlsToDelete.add(getPrimaryKeyTerm(virtualFile.url))
              return@forEach
            }
            if (!virtualFile.isDirectory) {
              filesToReindex.add(virtualFile)
            }
            else {
              // Should be used from readAction
              fileIndex.iterateContentUnderDirectory(virtualFile) { file ->
                if (!file.isDirectory) {
                  filesToReindex.add(file)
                }
                true // continue iteration
              }
            }
          }
        }

        if (filesToReindex.isEmpty() && urlsToDelete.isEmpty()) return
        LOG.debug { "Reindexing ${filesToReindex.size} files, deleting ${urlsToDelete.size} files" }

        val fileDataList = readAction { filesToReindex.map { getFileData(it) } }
        val termsAndDocs = fileDataList.map { buildDocument(it) }

        luceneIndex.processChanges { writer ->
          termsAndDocs.forEach { (term, doc) ->
            writer.updateDocument(term, doc)
          }

          urlsToDelete.forEach { writer.deleteDocuments(it) }

          LOG.debug("Reindexed all updated files for the next lucene index commit.")
        }
      }
    }
  }

  suspend fun awaitInitialIndexing(): Unit = initialIndexingCompleted.await()

  fun scheduleIndexingOp(op: LuceneFileIndexOperation) {
    if (!indexingEnabled) return

    // Since the channel is unbounded, the sending must succeed.
    val r = scheduledIndexingOps.trySend(op)
    if (r.isFailure) {
      throw IllegalStateException("The channel failed to send, even though its unbounded!")
    }

  }


  fun search(params: SeParams): Flow<LuceneFileSearchResult> {
    if (!indexingEnabled) return emptyFlow()

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
      //This will fire oftentimes, as each character typed by the user causes a new search.
      //And since the same deleted files are likely showing up repeatedly, there are a bunch of requests to delete the same file.
      //We could track the deleted files in the FilesProvider instead, but this would make the FileIndex interface more complex.
      //The debouncing/merging logic in place should be enough to handle this anyway.
      if (deletedFilesToRemoveFromIndex.isNotEmpty()) {
        LOG.debug {
          "Scheduling deletion of ${deletedFilesToRemoveFromIndex.size} files from index: ${
            deletedFilesToRemoveFromIndex.joinToString(", ",
                                                       limit = 10)
          }"
        }
        scheduleIndexingOp(LuceneFileIndexOperation.ReindexFiles(changedUrls = deletedFilesToRemoveFromIndex))
      }
    }
  }

  override fun dispose() {}

  internal fun getFileData(virtualFile: VirtualFile): FileData = FileData(
    url = virtualFile.url,
    name = virtualFile.name,
    relativePath = getRelativePathForFile(virtualFile),
  )

  @Throws(IOException::class)
  internal fun buildDocument(fileData: FileData): Pair<Term, Document> {
    val document = Document()
    document.add(StringField(FILE_URL, fileData.url, Field.Store.YES))
    //TODO does virtualFile include the extension?
    analyzeString(FileNameAnalyzer(), fileData.name).forEach { document.add(it) }
    analyzeString(FilePathAnalyzer(), fileData.relativePath).forEach { document.add(it) }
    val term = getPrimaryKeyTerm(fileData.url)
    return Pair(term, document)
  }

  private fun analyzeString(analyzer: Analyzer, field: String): List<Field> {
    val tokensByType = mutableMapOf<FileTokenType, MutableList<String>>()
    analyzer.use { analyzer ->
      val ts = analyzer.tokenStream("", field)
      val termAttr = ts.addAttribute(CharTermAttribute::class.java)
      val typeAttr = ts.addAttribute(MultiTypeAttribute::class.java)
      ts.reset()
      while (ts.incrementToken()) {
        val term = termAttr.toString()
        typeAttr.activeTypes().forEach { type ->
          tokensByType.getOrPut(type) { mutableListOf() }.add(term)
        }
      }
      ts.end()
    }
    return tokensByType.map { (type, tokens) ->
      TextField(type.type, ListTokenStream(tokens))
    }
  }

  private fun getRelativePathForFile(virtualFile: VirtualFile): String {
    // Try to get path relative to content root; fall back to filename if not in a content root
    val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(virtualFile) // REQUIRES READ-ACTION
    // TODO Find out if there are other ways to mock the files so that this special handling is not necessary?

    if (contentRoot != null) {
      return VfsUtil.getRelativePath(virtualFile, contentRoot) ?: virtualFile.name
    }

    // No content root: walk parent chain to build relative path (supports mock file trees in tests)
    val parts = mutableListOf<String>()
    var current: VirtualFile? = virtualFile
    while (current != null) {
      parts.add(0, current.name)
      current = current.parent
    }
    return parts.joinToString("/")
  }

  companion object {
    fun getInstance(project: Project): FileIndex = project.service()
    fun getInstanceIfEnabled(project: Project): FileIndex? = if (isIndexingEnabled()) project.service() else null
    fun isIndexingEnabled(): Boolean = Registry.`is`(LUCENE_INDEX_ENABLED_REGISTRY_KEY, true)

    val LOG: Logger = logger<FileIndex>() // #com.intellij.searchEverywhereLucene.backend.providers.files.FileIndex
    const val LUCENE_INDEX_ENABLED_REGISTRY_KEY: String = "search.everywhere.lucene.index.enabled"
    val PROGRESS_DISPLAY_DELAY: Duration = 500.milliseconds
    private const val INDEX_BATCH_SIZE: Int = 5000
    const val FILE_URL: String = "uri"
    val ANALYZER: Analyzer = FileSearchAnalyzer()

    private data class TokenEntry(
      val startOffset: Int,
      val endOffset: Int,
      val query: Query,
      val weight: Float,
    )

    fun buildQuery(params: SeParams, analyzer: Analyzer = ANALYZER): Query {
      val tokenStream = analyzer.tokenStream("", params.inputQuery)
      val termAttr = tokenStream.addAttribute(CharTermAttribute::class.java)
      val wordAttr = tokenStream.addAttribute(WordAttribute::class.java)
      val multiTypeAttr = tokenStream.addAttribute(MultiTypeAttribute::class.java)
      val offsetAttr = tokenStream.addAttribute(OffsetAttribute::class.java)

      val wordEntries = mutableMapOf<Int, MutableList<TokenEntry>>()

      tokenStream.reset()
      while (tokenStream.incrementToken()) {
        val wordIndex = wordAttr.wordIndex
        val termString = termAttr.toString()
        val typesToProcess = multiTypeAttr.activeTypes()
        val startOffset = offsetAttr.startOffset()
        val endOffset = offsetAttr.endOffset()

        val entries = wordEntries.getOrPut(wordIndex) { mutableListOf() }

        for (tokenType in typesToProcess) {
          val term = Term(tokenType.type, termString)

          val (boost, queries) = when (tokenType) {
            FileTokenType.FILENAME_PART,
            FileTokenType.FILENAME                         -> Pair(4f, listOf(scoringPrefixQuery(term)))
            FileTokenType.PATH_SEGMENT                     -> Pair(1.5f, listOf(scoringPrefixQuery(term)))
            FileTokenType.FILENAME_ABBREVIATION            -> Pair(0.6f, listOf(scoringPrefixQuery(term), ConstantScoreQuery(TermQuery(Term(FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS.type, termString)))))
            FileTokenType.PATH_SEGMENT_PREFIX              -> Pair(0.3f, listOf(scoringPrefixQuery(Term(FileTokenType.PATH_SEGMENT.type, termString))))
            FileTokenType.PATH,
            FileTokenType.FILETYPE                         -> Pair(0.5f, listOf(scoringPrefixQuery(term)))
            FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS -> throw IllegalArgumentException("Search Analyzer should not produce FILENAME_ABBREVIATION_WITH_SKIPS tokens")
          }

          for (q in queries) {
            entries.add(TokenEntry(startOffset, endOffset, q, boost))
          }
        }
      }
      tokenStream.end()
      tokenStream.close()

      if (wordEntries.isEmpty()) return BooleanQuery.Builder().build()

      val mainBq = BooleanQuery.Builder()
      for (index in wordEntries.keys.sorted()) {
        val entries = wordEntries[index]!!
        if (entries.isEmpty()) continue
        val (queryIntervals, relativeLength) = buildCompressedIntervals(entries)
        mainBq.add(IntervalSchedulingQuery(queryIntervals, relativeLength), BooleanClause.Occur.MUST)
      }

      val query = mainBq.build()
      LOG.debug { "Built query for \"${params.inputQuery}\": $query" }
      return query
    }

    /**
     * Converts [TokenEntry] list into [QueryInterval]s using a compressed position space.
     *
     * "Full-span" entries (those covering the entire [minStart, maxEnd) range, such as PATH
     * tokens) are excluded from gap detection so that separator characters (dots, slashes)
     * between core tokens do not create spurious coverage gaps. The resulting intervals have
     * positions mapped to a contiguous space with separator gaps removed.
     */
    private fun buildCompressedIntervals(entries: List<TokenEntry>): Pair<List<QueryInterval>, Int> {
      val minStart = entries.minOf { it.startOffset }
      val maxEnd = entries.maxOf { it.endOffset }
      val span = maxEnd - minStart

      if (span == 0) {
        // All tokens at the same point; trivially covered by any matching token.
        return Pair(entries.map { QueryInterval(0, 0, BoostQuery(it.query, it.weight)) }, 0)
      }

      // Core entries: not full-span. Full-span entries (e.g. PATH) bridge separators but
      // should not force coverage of separator positions on their own.
      val coreEntries = entries.filter { it.startOffset != minStart || it.endOffset != maxEnd }
        .ifEmpty { entries } // fallback: treat all as core if everything is full-span

      // Mark positions covered by core entries
      val coreCovered = BooleanArray(span)
      for (entry in coreEntries) {
        for (pos in entry.startOffset until entry.endOffset) {
          coreCovered[pos - minStart] = true
        }
      }

      // Build mapping: original-relative position → compressed position (gaps removed)
      val toCompressed = IntArray(span + 1)
      var comp = 0
      for (rel in 0 until span) {
        toCompressed[rel] = comp
        if (coreCovered[rel]) comp++
      }
      toCompressed[span] = comp

      val relativeLength = comp
      val queryIntervals = entries.map { entry ->
        QueryInterval(
          toCompressed[entry.startOffset - minStart],
          toCompressed[entry.endOffset - minStart],
          BoostQuery(entry.query, entry.weight),
        )
      }
      return Pair(queryIntervals, relativeLength)
    }

    private fun scoringPrefixQuery(term: Term): Query =
      LengthScoringPrefixQuery(term)

    private fun getPrimaryKeyTerm(url: String): Term {
      val term = Term(FILE_URL, url)
      return term
    }
  }
}

sealed class LuceneFileIndexOperation {
  data object IndexAll : LuceneFileIndexOperation()
  data class ReindexFiles(val changedFiles: Set<VirtualFile> = emptySet(), val changedUrls: Set<String> = emptySet()) :
    LuceneFileIndexOperation()
}

internal data class FileData(val url: String, val name: String, val relativePath: String)

/** Pre-tokenized stream that replays a fixed list of string terms. */
class ListTokenStream(private val tokens: List<String>) : TokenStream() {
  private val termAttr = addAttribute(CharTermAttribute::class.java)
  private var index = 0
  override fun incrementToken(): Boolean {
    if (index >= tokens.size) return false
    clearAttributes()
    termAttr.setEmpty().append(tokens[index++])
    return true
  }

  override fun reset() {
    super.reset(); index = 0
  }
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
    val got = select {
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
