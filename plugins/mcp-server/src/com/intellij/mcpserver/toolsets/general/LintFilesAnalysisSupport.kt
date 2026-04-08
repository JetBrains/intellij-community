@file:Suppress("ReplaceGetOrSet")

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
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.function.Function
import kotlin.coroutines.resume

private val LOG = logger<AnalysisToolset>()
private val LINT_FILES_COLLECTOR_OVERRIDE_KEY = Key.create<LintFilesCollectorOverride>("mcpserver.lintFilesCollectorOverride")

private typealias LintFilesCollectorOverride = suspend (
  LintFilesCollectorRequest,
  (AnalysisToolset.LintFileResult) -> Unit,
) -> Unit

@Internal
class LintFilesCollectorRequest internal constructor(
  @JvmField internal val requestedFiles: List<RequestedLintFile>,
  @JvmField val filePaths: List<String>,
  @JvmField val minSeverity: HighlightSeverity,
)

@Internal
internal suspend fun collectLintFiles(
  project: Project,
  request: LintFilesCollectorRequest,
  onFileResult: (AnalysisToolset.LintFileResult) -> Unit,
) {
  val override = project.getUserData(LINT_FILES_COLLECTOR_OVERRIDE_KEY)
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
  )
}

@TestOnly
internal suspend fun <T> withLintFilesCollectorOverride(
  project: Project,
  collector: suspend (LintFilesCollectorRequest, (AnalysisToolset.LintFileResult) -> Unit) -> Unit,
  action: suspend () -> T,
): T {
  val previousCollector = project.getUserData(LINT_FILES_COLLECTOR_OVERRIDE_KEY)
  project.putUserData(LINT_FILES_COLLECTOR_OVERRIDE_KEY, collector)
  try {
    return action()
  }
  finally {
    project.putUserData(LINT_FILES_COLLECTOR_OVERRIDE_KEY, previousCollector)
  }
}

internal suspend inline fun collectLintFileResults(
  project: Project,
  resolvedFiles: List<ResolvedLintFile>,
  minSeverity: HighlightSeverity,
  inspectionProfile: InspectionProfile,
  crossinline onFileResult: (AnalysisToolset.LintFileResult) -> Unit,
) {
  if (resolvedFiles.isEmpty()) {
    return
  }

  val daemonCodeAnalyzer = project.serviceAsync<DaemonCodeAnalyzer>() as DaemonCodeAnalyzerImpl
  val fileDocumentManager = serviceAsync<FileDocumentManager>()
  val psiManager = project.serviceAsync<PsiManager>()
  val codeAnalyzerSettings = serviceAsync<DaemonCodeAnalyzerSettings>()
  resolvedFiles.forEachConcurrent { resolvedFile ->
    val result = collectLintFileResult(
      resolvedFile = resolvedFile,
      minSeverity = minSeverity,
      inspectionProfile = inspectionProfile,
      psiManager = psiManager,
      fileDocumentManager = fileDocumentManager,
      codeAnalyzer = daemonCodeAnalyzer,
      codeAnalyzerSettings = codeAnalyzerSettings,
    )
    onFileResult(result)
  }
}

private suspend fun collectLintFileResult(
  resolvedFile: ResolvedLintFile,
  minSeverity: HighlightSeverity,
  inspectionProfile: InspectionProfile,
  psiManager: PsiManager,
  fileDocumentManager: FileDocumentManager,
  codeAnalyzer: DaemonCodeAnalyzerImpl,
  codeAnalyzerSettings: DaemonCodeAnalyzerSettings,
): AnalysisToolset.LintFileResult {
  val fileContext = readAction {
    val virtualFile = resolvedFile.virtualFile
    if (!virtualFile.isValid) {
      return@readAction null
    }

    val psiFile = psiManager.findFile(virtualFile) ?: return@readAction null
    if (!ProblemHighlightFilter.shouldProcessFileInBatch(psiFile)) {
      return@readAction null
    }

    val document = fileDocumentManager.getDocument(virtualFile) ?: mcpFail("Cannot get document: ${resolvedFile.relativePath}")
    LintFileContext(psiFile, document)
  }
  if (fileContext == null) {
    return createEmptyLintFileResult(resolvedFile.relativePath)
  }

  val range = ProperTextRange.create(0, fileContext.document.textLength)
  val daemonIndicator = DaemonProgressIndicator()
  var collectedHighlightInfos: List<HighlightInfo>? = null
  val highlightInfos = jobToIndicator(currentCoroutineContext().job, daemonIndicator) {
    HighlightingSessionImpl.runInsideHighlightingSession(fileContext.psiFile, null, range, false) { session ->
      (session as HighlightingSessionImpl).setMinimumSeverity(minSeverity)
      collectedHighlightInfos = runLintMainPasses(
        psiFile = fileContext.psiFile,
        document = fileContext.document,
        inspectionProfile = inspectionProfile,
        daemonIndicator = daemonIndicator,
        codeAnalyzer = codeAnalyzer,
        codeAnalyzerSettings = codeAnalyzerSettings,
      )
    }
    collectedHighlightInfos.orEmpty()
  }
  LOG.trace { "Main passes completed for ${resolvedFile.relativePath}, found ${highlightInfos.size} highlights" }
  return createLintFileResult(resolvedFile.relativePath, fileContext.document, highlightInfos, minSeverity)
}

private fun runLintMainPasses(
  psiFile: PsiFile,
  document: Document,
  inspectionProfile: InspectionProfile,
  daemonIndicator: DaemonProgressIndicator,
  codeAnalyzer: DaemonCodeAnalyzerImpl,
  codeAnalyzerSettings: DaemonCodeAnalyzerSettings,
): List<HighlightInfo> {
  val profileProvider = Function<InspectionProfile, InspectionProfileWrapper> { profile ->
    InspectionProfileWrapper(inspectionProfile, (profile as InspectionProfileImpl).profileManager)
  }
  var exception: ProcessCanceledException? = null
  var highlightInfos: List<HighlightInfo>? = null

  repeat(100) {
    daemonIndicator.checkCanceled()
    try {
      codeAnalyzerSettings.forceUseZeroAutoReparseDelayIn<RuntimeException> {
        InspectionProfileWrapper.runWithCustomInspectionWrapper(psiFile, profileProvider) {
          highlightInfos = codeAnalyzer.runMainPasses(psiFile, document, daemonIndicator)
        }
      }
      return highlightInfos ?: emptyList()
    }
    catch (e: ProcessCanceledException) {
      val cause = e.cause
      if (cause != null && cause.javaClass != Throwable::class.java) {
        throw e
      }
      daemonIndicator.checkCanceled()
      exception = e
    }
  }

  throw exception ?: ProcessCanceledException()
}

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

private fun createEmptyLintFileResult(filePath: String): AnalysisToolset.LintFileResult {
  LOG.trace { "Processed highlights for $filePath, found 0 problems" }
  return AnalysisToolset.LintFileResult(filePath = filePath, problems = emptyList(), timedOut = false)
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

private data class LintFileContext(
  @JvmField val psiFile: PsiFile,
  @JvmField val document: Document,
)
