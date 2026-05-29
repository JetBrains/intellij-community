package com.intellij.mcpserver.toolsets.general

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.util.awaitExternalChangesAndIndexing
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.function.Function
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AnalysisToolset>()

internal const val LINT_FILES_DEFAULT_TIMEOUT_MILLISECONDS_VALUE: Int = 120_000

private const val LINT_FILES_PER_FILE_TIMEOUT_MILLISECONDS: Int = 20_000
private const val LINT_FILES_TIMEOUT_HEADROOM_DIVISOR: Int = 20
private const val LINT_FILES_MIN_TIMEOUT_HEADROOM_MILLISECONDS: Int = 50
private const val LINT_FILES_MAX_TIMEOUT_HEADROOM_MILLISECONDS: Int = 5_000

internal data class LintFilesTimeoutContext(
  @JvmField val requestDeadlineMs: Long,
  @JvmField val perFileTimeoutMs: Int = LINT_FILES_PER_FILE_TIMEOUT_MILLISECONDS,
)

internal data class LintFilesBatchTimeouts(
  @JvmField val analysisTimeoutMs: Int,
  @JvmField val timeoutContext: LintFilesTimeoutContext,
)

internal fun createLintFilesBatchTimeouts(timeoutMs: Int, currentTimeMs: Long = System.currentTimeMillis()): LintFilesBatchTimeouts {
  val headroomMs = (timeoutMs / LINT_FILES_TIMEOUT_HEADROOM_DIVISOR)
    .coerceIn(LINT_FILES_MIN_TIMEOUT_HEADROOM_MILLISECONDS, LINT_FILES_MAX_TIMEOUT_HEADROOM_MILLISECONDS)
  val analysisTimeoutMs = (timeoutMs - headroomMs).coerceAtLeast(0)
  return LintFilesBatchTimeouts(
    analysisTimeoutMs = analysisTimeoutMs,
    timeoutContext = LintFilesTimeoutContext(requestDeadlineMs = currentTimeMs + analysisTimeoutMs),
  )
}

private typealias LintFilesCollectorOverride = suspend (
  LintFilesCollectorRequest,
  (AnalysisToolset.LintFileResult) -> Unit,
) -> Unit

private typealias LintBeforeMainPassesOverride = suspend (String) -> Unit

private typealias LintMainPassesRunnerOverride = suspend (LintMainPassesRunnerRequest) -> List<HighlightInfo>

@Service(Service.Level.PROJECT)
private class LintFilesAnalysisSupportState {
  val mainPassesMutex = Mutex()

  @Volatile
  var collectorOverride: LintFilesCollectorOverride? = null

  @Volatile
  var beforeMainPassesOverride: LintBeforeMainPassesOverride? = null

  @Volatile
  var mainPassesRunnerOverride: LintMainPassesRunnerOverride? = null
}

private suspend fun getLintFilesAnalysisSupportState(project: Project): LintFilesAnalysisSupportState = project.serviceAsync<LintFilesAnalysisSupportState>()

@Internal
class LintFilesCollectorRequest internal constructor(
  @JvmField internal val requestedFiles: List<RequestedLintFile>,
  @JvmField val filePaths: List<String>,
  @JvmField val minSeverity: HighlightSeverity,
)

@Internal
class LintMainPassesRunnerRequest internal constructor(
  @JvmField val filePath: String,
  @JvmField val attempt: Int,
)

@Internal
internal suspend fun collectLintFiles(
  project: Project,
  request: LintFilesCollectorRequest,
  onFileResult: (AnalysisToolset.LintFileResult) -> Unit,
  timeoutContext: LintFilesTimeoutContext? = null,
) {
  val override = getLintFilesAnalysisSupportState(project).collectorOverride
  if (override != null) {
    override(request, onFileResult)
    return
  }

  LOG.trace { "Awaiting external changes and indexing" }
  awaitExternalChangesAndIndexing(project)
  LOG.trace { "External changes and indexing completed" }

  val resolvedFiles = prepareLintFiles(request.requestedFiles)
  if (resolvedFiles.isEmpty()) {
    return
  }

  collectLintFileResults(
    project = project,
    resolvedFiles = resolvedFiles,
    minSeverity = request.minSeverity,
    inspectionProfile = project.serviceAsync<InspectionProjectProfileManager>().currentProfile,
    onFileResult = onFileResult,
    timeoutContext = timeoutContext,
  )
}

@TestOnly
internal suspend fun <T> withLintFilesCollectorOverride(
  project: Project,
  collector: suspend (LintFilesCollectorRequest, (AnalysisToolset.LintFileResult) -> Unit) -> Unit,
  action: suspend () -> T,
): T = withLintFilesAnalysisSupportOverride(
  project = project,
  newValue = collector,
  getCurrent = { it.collectorOverride },
  setCurrent = { state, value -> state.collectorOverride = value },
  action = action,
)

@TestOnly
internal suspend fun <T> withLintMainPassesRunnerOverride(
  project: Project,
  runner: suspend (LintMainPassesRunnerRequest) -> List<HighlightInfo>,
  action: suspend () -> T,
): T = withLintFilesAnalysisSupportOverride(
  project = project,
  newValue = runner,
  getCurrent = { it.mainPassesRunnerOverride },
  setCurrent = { state, value -> state.mainPassesRunnerOverride = value },
  action = action,
)

@TestOnly
internal suspend fun <T> withLintBeforeMainPassesOverride(
  project: Project,
  actionOverride: suspend (String) -> Unit,
  action: suspend () -> T,
): T = withLintFilesAnalysisSupportOverride(
  project = project,
  newValue = actionOverride,
  getCurrent = { it.beforeMainPassesOverride },
  setCurrent = { state, value -> state.beforeMainPassesOverride = value },
  action = action,
)

@TestOnly
private suspend fun <T, V> withLintFilesAnalysisSupportOverride(
  project: Project,
  newValue: V,
  getCurrent: (LintFilesAnalysisSupportState) -> V?,
  setCurrent: (LintFilesAnalysisSupportState, V?) -> Unit,
  action: suspend () -> T,
): T {
  val state = getLintFilesAnalysisSupportState(project)
  val previousValue = synchronized(state) {
    getCurrent(state).also {
      setCurrent(state, newValue)
    }
  }
  try {
    return action()
  }
  finally {
    synchronized(state) {
      setCurrent(state, previousValue)
    }
  }
}

internal suspend fun collectLintFileResults(
  project: Project,
  resolvedFiles: List<ResolvedLintFile>,
  minSeverity: HighlightSeverity,
  inspectionProfile: InspectionProfile,
  onFileResult: (AnalysisToolset.LintFileResult) -> Unit,
  timeoutContext: LintFilesTimeoutContext? = null,
) {
  if (resolvedFiles.isEmpty()) {
    return
  }

  val daemonCodeAnalyzer = project.serviceAsync<DaemonCodeAnalyzer>() as DaemonCodeAnalyzerImpl
  val fileDocumentManager = serviceAsync<FileDocumentManager>()
  val psiManager = project.serviceAsync<PsiManager>()
  val codeAnalyzerSettings = serviceAsync<DaemonCodeAnalyzerSettings>()
  // `runMainPasses()` is serialized separately; file preparation and result delivery can proceed concurrently.
  // Callers must treat `onFileResult` as a concurrent callback and restore request ordering themselves when needed.
  resolvedFiles.forEachConcurrent { resolvedFile ->
    val result = collectLintFileResult(
      project = project,
      resolvedFile = resolvedFile,
      minSeverity = minSeverity,
      inspectionProfile = inspectionProfile,
      psiManager = psiManager,
      fileDocumentManager = fileDocumentManager,
      codeAnalyzer = daemonCodeAnalyzer,
      codeAnalyzerSettings = codeAnalyzerSettings,
      timeoutContext = timeoutContext,
    )
    onFileResult(result)
  }
}

private suspend fun collectLintFileResult(
  project: Project,
  resolvedFile: ResolvedLintFile,
  minSeverity: HighlightSeverity,
  inspectionProfile: InspectionProfile,
  psiManager: PsiManager,
  fileDocumentManager: FileDocumentManager,
  codeAnalyzer: DaemonCodeAnalyzerImpl,
  codeAnalyzerSettings: DaemonCodeAnalyzerSettings,
  timeoutContext: LintFilesTimeoutContext? = null,
): AnalysisToolset.LintFileResult {
  val fileContext = readAction {
    val virtualFile = resolvedFile.virtualFile
    if (!virtualFile.isValid) {
      return@readAction LintFileContextStatus.NotAnalyzed("File is not valid or has been deleted")
    }

    val psiFile = psiManager.findFile(virtualFile)
    if (psiFile == null) {
      return@readAction LintFileContextStatus.NotAnalyzed("File type is not recognized or not supported for analysis")
    }
    if (!ProblemHighlightFilter.shouldProcessFileInBatch(psiFile)) {
      return@readAction LintFileContextStatus.NotAnalyzed("File is outside project content roots or in an excluded directory")
    }

    val document = fileDocumentManager.getDocument(virtualFile) ?: mcpFail("Cannot get document: ${resolvedFile.relativePath}")
    LintFileContextStatus.Ready(psiFile, document)
  }
  if (fileContext is LintFileContextStatus.NotAnalyzed) {
    return createNotAnalyzedLintFileResult(resolvedFile.relativePath, fileContext.reason)
  }
  val context = fileContext as LintFileContextStatus.Ready

  getLintFilesAnalysisSupportState(project).beforeMainPassesOverride?.invoke(resolvedFile.relativePath)

  val highlightInfos = runLintMainPasses(
    project = project,
    relativePath = resolvedFile.relativePath,
    psiFile = context.psiFile,
    document = context.document,
    minSeverity = minSeverity,
    inspectionProfile = inspectionProfile,
    codeAnalyzer = codeAnalyzer,
    codeAnalyzerSettings = codeAnalyzerSettings,
    timeoutContext = timeoutContext,
  ) ?: return createTimedOutLintFileResult(resolvedFile.relativePath)
  LOG.trace { "Main passes completed for ${resolvedFile.relativePath}, found ${highlightInfos.size} highlights" }
  return createLintFileResult(resolvedFile.relativePath, context.document, highlightInfos, minSeverity)
}

/**
 * Keep MCP's main-pass orchestration local. `DaemonCodeAnalyzerImpl.runMainPasses()` resets shared daemon state,
 * so concurrent MCP lint requests contend badly and can freeze the IDE. We intentionally do not reuse or edit the
 * legacy `MainPassesRunner` implementation for this tool.
 */
private suspend fun runLintMainPasses(
  project: Project,
  relativePath: String,
  psiFile: PsiFile,
  document: Document,
  minSeverity: HighlightSeverity,
  inspectionProfile: InspectionProfile,
  codeAnalyzer: DaemonCodeAnalyzerImpl,
  codeAnalyzerSettings: DaemonCodeAnalyzerSettings,
  timeoutContext: LintFilesTimeoutContext? = null,
): List<HighlightInfo>? {
  return getLintFilesMainPassesMutex(project).withLock {
    val fileTimeoutMs = timeoutContext?.fileTimeoutMs()
    if (fileTimeoutMs != null) {
      if (fileTimeoutMs <= 0) {
        return@withLock null
      }
      return@withLock withTimeoutOrNull(fileTimeoutMs.milliseconds) {
        runLintMainPassesLocked(
          project = project,
          relativePath = relativePath,
          psiFile = psiFile,
          document = document,
          minSeverity = minSeverity,
          inspectionProfile = inspectionProfile,
          codeAnalyzer = codeAnalyzer,
          codeAnalyzerSettings = codeAnalyzerSettings,
        )
      }
    }

    runLintMainPassesLocked(
      project = project,
      relativePath = relativePath,
      psiFile = psiFile,
      document = document,
      minSeverity = minSeverity,
      inspectionProfile = inspectionProfile,
      codeAnalyzer = codeAnalyzer,
      codeAnalyzerSettings = codeAnalyzerSettings,
    )
  }
}

private suspend fun runLintMainPassesLocked(
  project: Project,
  relativePath: String,
  psiFile: PsiFile,
  document: Document,
  minSeverity: HighlightSeverity,
  inspectionProfile: InspectionProfile,
  codeAnalyzer: DaemonCodeAnalyzerImpl,
  codeAnalyzerSettings: DaemonCodeAnalyzerSettings,
): List<HighlightInfo> {
  val profileProvider = Function<InspectionProfile, InspectionProfileWrapper> { profile ->
    InspectionProfileWrapper(inspectionProfile, (profile as InspectionProfileImpl).profileManager)
  }
  var exception: ProcessCanceledException? = null

  repeat(100) { attemptIndex ->
    currentCoroutineContext().ensureActive()
    val attempt = attemptIndex + 1
    val daemonIndicator = DaemonProgressIndicator()
    val listenerDisposable = Disposer.newDisposable()
    var canceledByWriteAction = false

    ApplicationManagerEx.getApplicationEx().addWriteActionListener(object : WriteActionListener {
      override fun beforeWriteActionStart(action: Class<*>) {
        canceledByWriteAction = true
        daemonIndicator.cancel("beforeWriteActionStart: $action")
      }
    }, listenerDisposable)

    try {
      if (ApplicationManagerEx.getApplicationEx().isWriteActionPending()) {
        canceledByWriteAction = true
        throw ProcessCanceledException()
      }

      return runLintMainPassesAttempt(
        project = project,
        relativePath = relativePath,
        attempt = attempt,
        psiFile = psiFile,
        document = document,
        minSeverity = minSeverity,
        daemonIndicator = daemonIndicator,
        profileProvider = profileProvider,
        codeAnalyzer = codeAnalyzer,
        codeAnalyzerSettings = codeAnalyzerSettings,
      )
    }
    catch (e: ProcessCanceledException) {
      currentCoroutineContext().ensureActive()
      if (!isRetriableProcessCanceledException(e)) {
        throw e
      }

      exception = e

      if (canceledByWriteAction || ApplicationManagerEx.getApplicationEx().isWriteActionPending()) {
        LOG.trace { "Retrying main passes for $relativePath after write action contention" }
        waitForWriteActionCompletion()
      }
    }
    finally {
      Disposer.dispose(listenerDisposable)
    }
  }

  throw exception ?: ProcessCanceledException()
}

private suspend fun runLintMainPassesAttempt(
  project: Project,
  relativePath: String,
  attempt: Int,
  psiFile: PsiFile,
  document: Document,
  minSeverity: HighlightSeverity,
  daemonIndicator: DaemonProgressIndicator,
  profileProvider: Function<InspectionProfile, InspectionProfileWrapper>,
  codeAnalyzer: DaemonCodeAnalyzerImpl,
  codeAnalyzerSettings: DaemonCodeAnalyzerSettings,
): List<HighlightInfo> {
  val override = getLintFilesAnalysisSupportState(project).mainPassesRunnerOverride
  if (override != null) {
    return override(LintMainPassesRunnerRequest(relativePath, attempt))
  }

  val range = ProperTextRange.create(0, document.textLength)
  var collectedHighlightInfos: List<HighlightInfo>? = null

  return jobToIndicator(currentCoroutineContext().job, daemonIndicator) {
    ProgressManager.checkCanceled()
    HighlightingSessionImpl.runInsideHighlightingSession(psiFile, null, range, false) { session ->
      (session as HighlightingSessionImpl).setMinimumSeverity(minSeverity)
      codeAnalyzerSettings.forceUseZeroAutoReparseDelayIn<RuntimeException> {
        InspectionProfileWrapper.runWithCustomInspectionWrapper(psiFile, profileProvider) {
          collectedHighlightInfos = codeAnalyzer.runMainPasses(psiFile, document, daemonIndicator)
        }
      }
    }
    collectedHighlightInfos.orEmpty()
  }
}

private fun isRetriableProcessCanceledException(e: ProcessCanceledException): Boolean {
  val cause = e.cause
  return cause == null || cause.javaClass == Throwable::class.java
}

private suspend fun waitForWriteActionCompletion() {
  val threadingSupport = ApplicationManager.getApplication().threadingSupport
  if (threadingSupport == null) {
    WriteAction.runAndWait<RuntimeException> { }
    return
  }

  suspendCancellableCoroutine { continuation ->
    val resumeContinuation = ResumeContinuationAction(continuation)
    threadingSupport.runWhenWriteActionIsCompleted(resumeContinuation::run)
  }
}

private class ResumeContinuationAction(continuation: CancellableContinuation<Unit>) {
  @Volatile
  private var continuation: CancellableContinuation<Unit>? = continuation

  init {
    continuation.invokeOnCancellation {
      this.continuation = null
    }
  }

  fun run() {
    continuation?.resume(Unit)
  }
}

private suspend fun getLintFilesMainPassesMutex(project: Project): Mutex = getLintFilesAnalysisSupportState(project).mainPassesMutex

private fun createLintFileResult(
  filePath: String,
  document: Document,
  highlightInfos: List<HighlightInfo>,
  minSeverity: HighlightSeverity,
): AnalysisToolset.LintFileResult {
  val problems = highlightInfos
    .asSequence()
    .filter { it.severity.myVal >= minSeverity.myVal }
    .map { createLintProblem(document, it) }
    .toList()

  LOG.trace { "Processed highlights for $filePath, found ${problems.size} problems" }

  return AnalysisToolset.LintFileResult(filePath = filePath, problems = problems, timedOut = false)
}

private fun createNotAnalyzedLintFileResult(filePath: String, reason: String): AnalysisToolset.LintFileResult {
  LOG.trace { "File not analyzed: $filePath, reason: $reason" }
  return AnalysisToolset.LintFileResult(filePath = filePath, notAnalyzedReason = reason,
                                         problems = emptyList(), timedOut = false)
}

private fun createTimedOutLintFileResult(filePath: String): AnalysisToolset.LintFileResult {
  LOG.trace { "Timed out while collecting highlights for $filePath" }
  return AnalysisToolset.LintFileResult(filePath = filePath, problems = emptyList(), timedOut = true)
}

private fun createLintProblem(document: Document, info: HighlightInfo): AnalysisToolset.LintProblem {
  val startLine = document.getLineNumber(info.startOffset)
  val lineStartOffset = document.getLineStartOffset(startLine)
  val lineEndOffset = document.getLineEndOffset(startLine)
  val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))
  val column = info.startOffset - lineStartOffset

  return AnalysisToolset.LintProblem(
    severity = info.severity.name,
    description = info.description ?: "",
    lineText = lineText,
    line = startLine + 1,
    column = column + 1,
  )
}

internal data class ResolvedLintFile(
  @JvmField val relativePath: String,
  @JvmField val virtualFile: VirtualFile,
)

internal suspend fun prepareLintFiles(requestedFiles: List<RequestedLintFile>): List<ResolvedLintFile> {
  if (requestedFiles.isEmpty()) {
    return emptyList()
  }

  val localFileSystem = LocalFileSystem.getInstance()
  val resolvedFiles = arrayOfNulls<ResolvedLintFile>(requestedFiles.size)
  val missingIndexes = ArrayList<Int>()
  val missingPaths = ArrayList<java.nio.file.Path>()
  // Keep already-materialized files and refresh only VFS misses.
  for ((index, requestedFile) in requestedFiles.withIndex()) {
    val virtualFile = localFileSystem.findFileByNioFile(requestedFile.resolvedPath)
    if (virtualFile == null) {
      missingIndexes.add(index)
      missingPaths.add(requestedFile.resolvedPath)
    }
    else {
      resolvedFiles[index] = ResolvedLintFile(requestedFile.relativePath, virtualFile)
    }
  }

  if (missingPaths.isNotEmpty()) {
    // collectLintFiles() has already awaited awaitExternalChangesAndIndexing(project), so watcher events and
    // indexing are caught up. At this point we only need the cheaper batched refresh to materialize missing
    // paths in VFS instead of forcing a heavier per-file refresh.
    suspendCancellableCoroutine { cont ->
      localFileSystem.refreshNioFiles(/* files = */ missingPaths, /* async = */ true, /* recursive = */ false) {
        cont.resume(Unit)
      }
    }

    for (index in missingIndexes) {
      val requestedFile = requestedFiles[index]
      val virtualFile = localFileSystem.findFileByNioFile(requestedFile.resolvedPath)
                        ?: mcpFail("Cannot access file: ${requestedFile.requestedPath}")
      resolvedFiles[index] = ResolvedLintFile(requestedFile.relativePath, virtualFile)
    }
  }

  return resolvedFiles.map(::requireNotNull)
}

private sealed class LintFileContextStatus {
  data class Ready(val psiFile: PsiFile, val document: Document) : LintFileContextStatus()
  data class NotAnalyzed(val reason: String) : LintFileContextStatus()
}

private fun LintFilesTimeoutContext.fileTimeoutMs(currentTimeMs: Long = System.currentTimeMillis()): Int {
  val remainingRequestBudgetMs = (requestDeadlineMs - currentTimeMs).coerceAtLeast(0L)
  return minOf(remainingRequestBudgetMs, perFileTimeoutMs.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
