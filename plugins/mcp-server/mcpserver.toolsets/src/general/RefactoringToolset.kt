@file:Suppress("FunctionName")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.SymbolInfo
import com.intellij.mcpserver.util.awaitExternalChangesAndIndexing
import com.intellij.mcpserver.util.checkIndexingInProgress
import com.intellij.mcpserver.util.getElementSymbolInfo
import com.intellij.mcpserver.util.isProjectIndexing
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.rename.HeadlessRenameFailure
import com.intellij.refactoring.rename.HeadlessRenameProcessor
import com.intellij.refactoring.rename.HeadlessRenameResult
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.MultiMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

class RefactoringToolset : McpToolset {
  override fun displayName(): String = McpServerBundle.message("toolset.display.name.refactoring")

  override fun displayDescription(toolName: String): String = McpServerBundle.message("tool.description.$toolName")

  @Suppress("SplitModeApiUsage")
  @McpToolHints(readOnlyHint = FALSE, destructiveHint = FALSE, idempotentHint = FALSE, openWorldHint = FALSE)
  @McpTool
  @McpDescription("""
    |Rename a symbol and every reference to it. Set preview=true to analyze the rename and write nothing.
    |The IDE resolves the symbol through the PSI, so it also updates a reference that a text search cannot
    |find. Always prefer this tool over a text replacement.
    |
    |Point at the symbol with ONE of these, in order of preference: contextSnippet, then line and column.
    |With neither of them, the file must declare one symbol with this name. symbolName is always required.
    |A position that resolves to another name refuses the rename.
    |
    |targetIndex is not a third way to point at the symbol. It picks from a candidates list an earlier call
    |returned, so repeat that call with every argument it had, and add targetIndex.
    |
    |Read `applied`. It is the only success signal. Nothing is written in part, except after
    |error.kind=write_failed, and its hint says what was written.
    |ok=false with candidates asks you to pick one with targetIndex. ok=false with conflicts asks for another
    |newName. ok=false with error tells you in `hint` what to correct.
    |
    |Automatic renamers do not run, unlike a rename in the IDE. A library or compiled symbol cannot be renamed.
  """)
  suspend fun rename_refactoring(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    pathInProject: String,
    @McpDescription("Exact, case-sensitive name of the symbol to rename.")
    symbolName: String,
    @McpDescription("New name for the symbol. It must be a valid identifier for the symbol's language.")
    newName: String,
    @McpDescription("Optional. Exact fragment of the file that shows the symbol. It must match the file once, and contain symbolName.")
    contextSnippet: String? = null,
    @McpDescription("Optional. 1-based line of the symbol. Copy it from search_symbol output. Needs column too.")
    line: Int? = null,
    @McpDescription("Optional. 1-based column of the symbol. Copy it from search_symbol output. Needs line too.")
    column: Int? = null,
    @McpDescription("Optional. 1-based pick from the candidates list an earlier call returned. Repeat every other argument of that call.")
    targetIndex: Int? = null,
    @McpDescription("Optional. Analyze only: report affects and conflicts, and write nothing. The default is false.")
    preview: Boolean = false,
  ): RenameResult {
    if (pathInProject.isBlank()) mcpFail("pathInProject is empty")
    if (symbolName.isBlank()) mcpFail("symbolName is empty")
    if (newName.isBlank()) mcpFail("newName is empty")
    if ((line == null) != (column == null)) mcpFail("line and column must be given together")

    val project = currentCoroutineContext().project
    currentCoroutineContext().reportToolActivity(
      McpServerBundle.message("tool.activity.renaming.symbol", symbolName, newName, pathInProject))

    val resolvedPath = project.resolveInProject(pathInProject)
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(resolvedPath)
                      ?: VirtualFileManager.getInstance().refreshAndFindFileByNioPath(resolvedPath)
                      ?: mcpFail("File not found: $pathInProject")
    awaitExternalChangesAndIndexing(project)
    // The wait above ends when the indexes are ready. They can go back to work at once, because the
    // IDE indexes in the background. A rename on a partial index misses a usage, and a rename that
    // misses a usage breaks the code.
    if (isProjectIndexing(project)) {
      val kind = HeadlessRenameFailure.INDEX_NOT_READY
      return failed(errorKind(kind), failureHint(kind, null))
    }

    val request = RenameTargetRequest(symbolName, contextSnippet, line, column, targetIndex)
    val (result, partialResultReason) = checkIndexingInProgress(project) {
      rename(project, virtualFile, pathInProject, request, newName, preview)
    }
    return result.copy(partialResultReason = partialResultReason)
  }

  private suspend fun rename(
    project: Project,
    virtualFile: VirtualFile,
    pathInProject: String,
    request: RenameTargetRequest,
    newName: String,
    preview: Boolean,
  ): RenameResult {
    val preparation = readAction { prepare(project, virtualFile, pathInProject, request, newName) }
    val ready = when (preparation) {
      is Preparation.Stop -> return preparation.result
      is Preparation.Ready -> preparation
    }

    // The old path has to be taken now: after the rename the VFS no longer resolves it.
    val pathBefore = virtualFile.path
    val relativePathBefore = project.projectDirectory.relativizeIfPossible(virtualFile)

    // analyze() writes nothing and needs a read action only. It must stay off EDT, because the
    // Kotlin Analysis API refuses to resolve there. plan.apply() below is the step that writes.
    val analysis = readAction {
      ready.element()?.let { HeadlessRenameProcessor.analyze(project, it, newName) }
    } ?: return staleTarget(ready.resolvedSymbol)
    val plan = when (analysis) {
      is HeadlessRenameResult.Planned -> analysis.plan
      // The language states no headless rename support. See legacyRename.
      is HeadlessRenameResult.Failed ->
        if (analysis.kind == HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED) {
          return legacyRename(project, virtualFile, pathBefore, relativePathBefore, ready, newName, preview)
        }
        else return outcomeResult(project, analysis, ready.resolvedSymbol)
      else -> return outcomeResult(project, analysis, ready.resolvedSymbol)
    }
    val resolvedSymbol = readAction { plan.primaryElement?.let { getElementSymbolInfo(it) } } ?: ready.resolvedSymbol

    if (preview) {
      return RenameResult(
        ok = true,
        applied = false,
        resolvedSymbol = resolvedSymbol,
        affects = RenameAffects(files = plan.affectedFiles.size, usages = plan.affectedUsages),
        changedFiles = relativePaths(project, plan.affectedFiles),
        note = joinNotes(plan.notes),
      )
    }

    val outcome = withContext(Dispatchers.EDT) { writeIntentReadAction { plan.apply() } }
    if (outcome is HeadlessRenameResult.Applied) {
      return RenameResult(
        ok = true,
        applied = true,
        resolvedSymbol = resolvedSymbol,
        affects = RenameAffects(files = outcome.affectedFiles.size, usages = outcome.affectedUsages),
        changedFiles = relativePaths(project, outcome.affectedFiles),
        renamedFile = renamedFile(project, virtualFile, pathBefore, relativePathBefore),
        note = joinNotes(outcome.notes + listOfNotNull(skippedFilesNote(outcome.skippedFiles))),
      )
    }
    return outcomeResult(project, outcome, resolvedSymbol)
  }

  /**
   * Renames with the platform engine, for a language that states no headless rename support.
   *
   * It keeps the tool working where [HeadlessRenameProcessor] refuses, with the guarantees the tool
   * had before that engine. Those guarantees are weaker, and the caller must know it:
   * - The rename can stop on a dialog. The language asks a question that has no default answer yet.
   * - It reports no reason, except a conflict. `applied` says whether anything was written.
   * - It reports no read-only file, no affected file list and no usage count.
   *
   * A processor leaves this path by implementing `HeadlessRenamePsiElementProcessor` and by a
   * registration in its extension point.
   */
  private suspend fun legacyRename(
    project: Project,
    virtualFile: VirtualFile,
    pathBefore: String,
    relativePathBefore: String,
    ready: Preparation.Ready,
    newName: String,
    preview: Boolean,
  ): RenameResult {
    // A preview reports the affected files and the conflicts, and only the headless engine finds
    // them. This path has no engine to ask, so it refuses instead of answering ok with nothing in
    // it. An ok with no affects and no conflicts reads as "the rename is safe", and the rename
    // without preview can still stop on a conflict.
    if (preview) {
      val language = ready.resolvedSymbol?.language ?: "This language"
      return RenameResult(
        ok = false,
        applied = false,
        resolvedSymbol = ready.resolvedSymbol,
        error = RenameError(
          "preview_not_supported",
          "$language has no headless rename support, so a preview reports nothing. Nothing was analyzed " +
          "and nothing changed. Call again without preview to run the rename itself."),
      )
    }
    // The constructor reads the PSI: RenameProcessor builds the command name from the element. So it
    // needs a read action, and the element has to be valid when it runs.
    val processor = readAction {
      ready.element()?.let { LegacyRenameProcessor(project, it, newName) }
    } ?: return staleTarget(ready.resolvedSymbol)
    try {
      withContext(Dispatchers.EDT) { writeIntentReadAction { processor.run() } }
    }
    catch (_: ConflictsFoundException) {
      return RenameResult(
        ok = false,
        applied = false,
        resolvedSymbol = ready.resolvedSymbol,
        error = RenameError("conflicts_found", "A conflict refused the rename and nothing changed. Choose another newName."),
      )
    }
    if (!processor.applied) {
      // A refusal means the write started and stopped, so the code can hold a part of the rename. A
      // stop with no refusal means the engine never reached the write, and nothing changed.
      return RenameResult(
        ok = false,
        applied = false,
        resolvedSymbol = ready.resolvedSymbol,
        error = if (!processor.refused) RenameError("not_applied", LEGACY_HINT)
        else RenameError("write_failed",
                         "The rename stopped while it wrote. " +
                         "${processor.refusalMessage ?: "The language reported no reason."} " +
                         "Read the files again before you retry."),
      )
    }
    return RenameResult(
      ok = true,
      applied = true,
      resolvedSymbol = ready.resolvedSymbol,
      renamedFile = renamedFile(project, virtualFile, pathBefore, relativePathBefore),
      note = LEGACY_HINT,
    )
  }

  /** Maps every outcome that is not a plan and not an applied rename. */
  private fun outcomeResult(project: Project, outcome: HeadlessRenameResult, resolvedSymbol: SymbolInfo?): RenameResult =
    when (outcome) {
      is HeadlessRenameResult.Refused -> RenameResult(
        ok = false,
        applied = false,
        resolvedSymbol = resolvedSymbol,
        conflicts = outcome.conflicts.map { RenameConflict(it.description, relativePath(project, it.filePath)) },
        error = RenameError("conflicts_found", "A conflict refused the rename and nothing changed. Choose another newName."),
      )
      is HeadlessRenameResult.Failed -> RenameResult(
        ok = false,
        applied = false,
        resolvedSymbol = resolvedSymbol,
        error = RenameError(errorKind(outcome.kind), failureHint(outcome.kind, outcome.detail)),
      )
      is HeadlessRenameResult.Planned, is HeadlessRenameResult.Applied -> mcpFail("Unexpected rename outcome")
    }

  private fun joinNotes(notes: List<String>): String? = notes.takeIf { it.isNotEmpty() }?.joinToString(" ")

  private fun prepare(
    project: Project,
    virtualFile: VirtualFile,
    pathInProject: String,
    request: RenameTargetRequest,
    newName: String,
  ): Preparation {
    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                   ?: mcpFail("Cannot read file: $pathInProject")
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                  ?: mcpFail("Cannot build a syntax tree for file: $pathInProject")

    val resolution = resolveRenameTarget(psiFile, document, request)
    val target = when (resolution) {
      is RenameTargetResolution.Resolved -> resolution.element
      is RenameTargetResolution.Ambiguous -> return Preparation.Stop(ambiguousResult(resolution.candidates, request.symbolName))
      is RenameTargetResolution.Unresolved -> return Preparation.Stop(
        failed(resolution.kind.name.lowercase(), resolution.hint))
    }

    // The substitution of an override, a constructor or an accessor happens in
    // HeadlessRenameProcessor.analyze, because it requires EDT. So does the renamable check.
    val symbol = getElementSymbolInfo(target)

    if ((target as? PsiNamedElement)?.name == newName) {
      return Preparation.Stop(failed("new_name_matches_current", "The symbol already has the name '$newName'.").copy(resolvedSymbol = symbol))
    }
    if (!RenameUtil.isValidName(project, target, newName)) {
      return Preparation.Stop(
        failed("new_name_invalid", "'$newName' is not a valid identifier for ${symbol?.language ?: "this language"}.")
          .copy(resolvedSymbol = symbol))
    }
    return Preparation.Ready(SmartPointerManager.createPointer(target), symbol)
  }

  private fun ambiguousResult(candidates: List<PsiElement>, symbolName: String): RenameResult {
    // Never drop a candidate and never reorder one: resolveRenameTarget resolves targetIndex against
    // this list, and it rebuilds the list from the same arguments.
    val entries = candidates.mapIndexed { index, element ->
      RenameCandidate(targetIndex = index + 1, kind = UsageViewUtil.getType(element), symbol = getElementSymbolInfo(element))
    }
    return RenameResult(
      ok = false,
      applied = false,
      candidates = entries,
      error = RenameError(
        "ambiguous_symbol",
        "'$symbolName' matches ${candidates.size} symbols here. Nothing changed. " +
        "Call again with every argument of this call plus targetIndex, or narrow the call with a contextSnippet " +
        "that shows the one you mean.",
      ),
    )
  }

  /** The target is gone, because the code changed between the two read actions of the rename. */
  private fun staleTarget(resolvedSymbol: SymbolInfo?): RenameResult {
    val kind = HeadlessRenameFailure.PLAN_STALE
    return failed(errorKind(kind), failureHint(kind, null)).copy(resolvedSymbol = resolvedSymbol)
  }

  private fun renamedFile(project: Project, virtualFile: VirtualFile, pathBefore: String, relativePathBefore: String): RenamedFile? {
    if (!virtualFile.isValid || virtualFile.path == pathBefore) return null
    return RenamedFile(
      previousPath = relativePathBefore,
      path = project.projectDirectory.relativizeIfPossible(virtualFile),
    )
  }

  private fun skippedFilesNote(skippedFiles: List<String>): String? {
    if (skippedFiles.isEmpty()) return null
    return "The rename of these files was skipped, because the new name is already taken: ${skippedFiles.joinToString(", ")}."
  }

  private fun relativePaths(project: Project, files: List<VirtualFile>): List<String> {
    val projectDirectory = project.projectDirectory
    return files.map { projectDirectory.relativizeIfPossible(it) }.sorted()
  }

  @Suppress("SplitModeApiUsage")
  private fun relativePath(project: Project, absolutePath: String?): String? {
    if (absolutePath == null) return null
    val path = absolutePath.toNioPathOrNull() ?: return absolutePath
    val file = VirtualFileManager.getInstance().findFileByNioPath(path)
               ?: VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
               ?: return absolutePath
    return project.projectDirectory.relativizeIfPossible(file)
  }

  private fun failed(kind: String, hint: String): RenameResult =
    RenameResult(ok = false, applied = false, error = RenameError(kind, hint))

  /**
   * The wire name of a failure.
   *
   * Every kind is mapped by hand on purpose. [HeadlessRenameFailure] is experimental, so a rename of
   * a constant there must break this build, and not the contract of the tool in silence.
   * `reference/failures.md` in the `refactor-jvm-jb` skill documents these names.
   */
  private fun errorKind(kind: HeadlessRenameFailure): String = when (kind) {
    HeadlessRenameFailure.TARGET_NOT_RENAMABLE -> "not_renamable"
    HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED -> "language_not_supported"
    HeadlessRenameFailure.UNSUPPORTED_REFERENCE_LANGUAGE -> "unsupported_reference_language"
    HeadlessRenameFailure.INDEX_NOT_READY -> "index_not_ready"
    HeadlessRenameFailure.NEW_NAME_REFUSED -> "new_name_refused"
    HeadlessRenameFailure.READ_ONLY_USAGES -> "read_only_usages"
    HeadlessRenameFailure.INCOMPLETE_USE_SCOPE -> "incomplete_use_scope"
    HeadlessRenameFailure.PLAN_STALE -> "plan_stale"
    HeadlessRenameFailure.WRITE_FAILED -> "write_failed"
    HeadlessRenameFailure.UNKNOWN -> "unknown"
  }

  private fun failureHint(kind: HeadlessRenameFailure, detail: String?): String = when (kind) {
    HeadlessRenameFailure.TARGET_NOT_RENAMABLE ->
      detail ?: "This symbol cannot be renamed. A library or compiled symbol has to change upstream."
    HeadlessRenameFailure.PLAN_STALE ->
      "The rename did not start from the current code, so nothing changed. Read the file again and retry."
    HeadlessRenameFailure.READ_ONLY_USAGES ->
      "${detail ?: "A file that the rename must change cannot be written."} Nothing was renamed. Make those files " +
      "writable. A file that only the VCS holds needs a checkout, which this tool does not do on its own."
    HeadlessRenameFailure.INCOMPLETE_USE_SCOPE ->
      "The project holds an unloaded module that uses this symbol, so the rename would leave a stale reference. Load the module first."
    HeadlessRenameFailure.INDEX_NOT_READY ->
      "The indexes were not ready, so the usage set would be incomplete. Retry when indexing finishes."
    HeadlessRenameFailure.UNSUPPORTED_REFERENCE_LANGUAGE ->
      "A reference belongs to a language that cannot take part in this rename, so nothing was renamed."
    HeadlessRenameFailure.NEW_NAME_REFUSED -> detail ?: "The symbol cannot be renamed to this name."
    HeadlessRenameFailure.WRITE_FAILED ->
      "The rename stopped while it wrote. ${detail ?: "No reason was reported."} Read the files again before you retry."
    HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED -> LEGACY_HINT
    HeadlessRenameFailure.UNKNOWN -> detail ?: "The rename failed for an unreported reason."
  }

  private sealed interface Preparation {
    /**
     * The target the rename starts from.
     *
     * It is a pointer and not an element, because the rename resolves the target in one read action
     * and analyzes it in another. The code can change in between, and a raw element would go stale
     * with no report. [element] answers null then, and the caller reports `plan_stale`.
     */
    class Ready(private val pointer: SmartPsiElementPointer<PsiElement>, val resolvedSymbol: SymbolInfo?) : Preparation {
      @RequiresReadLock
      fun element(): PsiElement? = pointer.element?.takeIf { it.isValid }
    }

    class Stop(val result: RenameResult) : Preparation
  }

  /**
   * The rename engine this tool drove before [HeadlessRenameProcessor].
   *
   * It is the earlier `McpRenameProcessor`, with one addition: [applied]. [performRefactoring] is
   * the only step of the platform engine that writes, so a run that reached it wrote, and a run that
   * stopped earlier wrote nothing. A stop can come from a dialog, from a read-only file or from the
   * usage preview, and this class cannot tell them apart.
   *
   * See [IJPL-196163](https://youtrack.jetbrains.com/issue/IJPL-196163).
   */
  private class LegacyRenameProcessor(
    project: Project,
    val element: PsiElement,
    val newName: String,
  ) : RenameProcessor(project, element, newName, false, false) {
    var applied: Boolean = false
      private set

    /**
     * True when the language refused the write.
     *
     * It is a flag of its own, and not a null check on [refusalMessage]. An
     * [IncorrectOperationException] can carry no message, and a refusal read from the message alone
     * would then report a partial write as a success.
     */
    var refused: Boolean = false
      private set

    /** What the language reported about the refusal, or null when it reported nothing. */
    var refusalMessage: String? = null
      private set

    override fun isPreviewUsages(usages: Array<out UsageInfo?>): Boolean {
      return false
    }

    override fun showAutomaticRenamingDialog(automaticVariableRenamer: AutomaticRenamer?) = false

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo?>?>): Boolean {
      val usagesIn: Array<UsageInfo?> = refUsages.get() ?: return false
      val conflicts = MultiMap<PsiElement?, String?>()

      RenameUtil.addConflictDescriptions(usagesIn, conflicts)
      RenamePsiElementProcessor.forElement(element).findExistingNameConflicts(
        element, newName, conflicts, myAllRenames
      )
      if (!conflicts.isEmpty) {
        throw ConflictsFoundException()
      }
      return true
    }

    override fun showRenameErrorMessage(e: IncorrectOperationException, element: PsiElement) {
      refused = true
      refusalMessage = e.message
    }

    override fun performRefactoring(usages: Array<UsageInfo>) {
      super.performRefactoring(usages)
      applied = !refused
    }
  }

  private class ConflictsFoundException : Exception() {
    override val message: String = "Conflicts were found during renaming"
  }

  private companion object {
    private const val LEGACY_HINT: String =
      "This language has no headless rename support, so the rename ran on the legacy path. " +
      "It reports whether it wrote, and no reason. It can also stop on a dialog in the IDE."
  }

  @Serializable
  data class RenameResult(
    /** False when nothing was renamed. Read [applied] to learn whether a write happened. */
    val ok: Boolean,
    /** True only when the rename was written. */
    val applied: Boolean,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val resolvedSymbol: SymbolInfo? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val affects: RenameAffects? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val changedFiles: List<String>? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val renamedFile: RenamedFile? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val candidates: List<RenameCandidate>? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val conflicts: List<RenameConflict>? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val error: RenameError? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val note: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val partialResultReason: String? = null,
  )

  /** The blast radius: the files that hold a usage or a declaration, and the number of usages. */
  @Serializable
  data class RenameAffects(val files: Int, val usages: Int)

  @Serializable
  data class RenamedFile(val previousPath: String, val path: String)

  /** One symbol the name matched. Pass [targetIndex] back to pick it. */
  @Serializable
  data class RenameCandidate(
    val targetIndex: Int,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val kind: String? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val symbol: SymbolInfo? = null,
  )

  @Serializable
  data class RenameConflict(
    val description: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val filePath: String? = null,
  )

  @Serializable
  data class RenameError(val kind: String, val hint: String)
}
