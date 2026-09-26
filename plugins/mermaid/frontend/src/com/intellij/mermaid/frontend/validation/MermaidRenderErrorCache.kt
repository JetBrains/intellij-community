// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.frontend.validation

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.validation.MermaidSyntaxProblem
import com.intellij.mermaid.lang.validation.MermaidSyntaxValidator
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The last verdict [MermaidSyntaxValidator] gave for each file, refreshed off the daemon.
 *
 * Exists because asking mermaid is asynchronous -- it crosses into JCEF and waits on a JS promise -- while
 * the only annotator kind the frontend may register is the synchronous one. Reporting the previous answer
 * while the next is computed is what makes that work, and it costs less than it looks: the preview-backed
 * validators already report what was last *rendered* rather than what is currently typed, so the answer
 * lagged the text by a keystroke before this cache existed too.
 */
@Service(Service.Level.PROJECT)
internal class MermaidRenderErrorCache(private val project: Project, private val scope: CoroutineScope) {
  private class Entry(val stamp: Long, val problems: List<MermaidSyntaxProblem>)

  private val entries = ConcurrentHashMap<VirtualFile, Entry>()
  private val refreshing = ConcurrentHashMap.newKeySet<VirtualFile>()

  /**
   * Problems from the last completed validation of [file], scheduling a new one when [document] has moved on
   * since that answer was recorded.
   */
  fun problemsFor(file: MermaidFile, document: Document): List<MermaidSyntaxProblem> {
    val virtualFile = file.virtualFile ?: return emptyList()
    if (MermaidSyntaxValidator.EP_NAME.extensionList.isEmpty()) return emptyList()

    val entry = entries[virtualFile]
    if (entry?.stamp != document.modificationStamp) scheduleRefresh(virtualFile, document)
    return entry?.problems.orEmpty()
  }

  private fun scheduleRefresh(virtualFile: VirtualFile, document: Document) {
    // One refresh per file at a time. The daemon asks on every pass, and each pass would otherwise start
    // another browser round-trip for an answer already on its way.
    if (!refreshing.add(virtualFile)) return

    scope.launch {
      try {
        val (stamp, text) = readAction { document.modificationStamp to document.text }
        // An unanswered check is recorded as "no problems" rather than left absent: nothing is shown either
        // way, and an absent entry would look stale on the next pass and schedule the same doomed refresh
        // for as long as the file stays open.
        val problems = validate(virtualFile, text).orEmpty()
        val previous = entries.put(virtualFile, Entry(stamp, problems))
        if (previous?.problems != problems) restartDaemon(virtualFile)
      }
      finally {
        refreshing.remove(virtualFile)
      }
    }
  }

  /**
   * Null when no validator could run at all -- no preview open, browser not ready -- which stays distinct
   * from an empty list all the way to the caller: a check nobody could perform is not a clean bill of health.
   */
  private suspend fun validate(virtualFile: VirtualFile, text: String): List<MermaidSyntaxProblem>? {
    // Iterated by hand rather than through ExtensionPointName.computeSafeIfAny because validation suspends
    // and the safe helpers take a plain Function. The failure handling is the one they apply: cancellation
    // propagates, and anything else a validator throws costs only that validator.
    for (validator in MermaidSyntaxValidator.EP_NAME.extensionList) {
      val problems = try {
        validator.validate(project, virtualFile, text)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        thisLogger().error("Mermaid validator ${validator.javaClass.name} failed", e)
        continue
      }
      if (problems == null) {
        thisLogger().debug("Mermaid validation unavailable from ${validator.javaClass.simpleName}")
        continue
      }
      thisLogger().debug("Mermaid reported ${problems.size} problem(s) in ${virtualFile.name}")
      return problems
    }
    return null
  }

  private suspend fun restartDaemon(virtualFile: VirtualFile) {
    readAction {
      // Re-resolved rather than carried across the refresh: background code should not hold PSI, and the
      // file may have been reparsed or closed while mermaid was thinking.
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile)?.takeIf { it.isValid } ?: return@readAction
      DaemonCodeAnalyzer.getInstance(project).restart(psiFile, this)
    }
  }
}
