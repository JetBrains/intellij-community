// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.PairProcessor

/**
 * Computes the suppression codes of the problems currently on a given line, so the "Specify inspection codes"
 * quick fix ([com.jetbrains.python.inspections.typeignore.PyTypeIgnoreWithoutCodeInspection]) can turn a
 * code-less `# type: ignore` into one that lists exactly what it covers.
 *
 * It runs the enabled local inspections restricted to that one line with `ignoreSuppressedElements = false`,
 * so problems that a bare `# type: ignore` on the line would otherwise hide are still reported. Must be called
 * under a read action off the EDT (e.g. from a ModCommand quick fix).
 */
object PyIgnoreCodeComputer {

  /**
   * The ordered, de-duplicated codes for the problems on [lineNumber] of [file] (a `PyTypeChecker` problem
   * contributes its granular code such as `unsupported-operator`; any other inspection contributes its
   * kebab-case alias or raw suppress id). [excludeToolIds] drops inspections whose own diagnostics should not
   * be listed (notably the inspection that offers this fix).
   */
  fun codesOnLine(file: PsiFile, lineNumber: Int, excludeToolIds: Set<String>): List<String> {
    val project = file.project
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return emptyList()
    if (lineNumber < 0 || lineNumber >= document.lineCount) return emptyList()
    val lineRange = TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber))

    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    val wrappers = profile.getInspectionTools(file)
      .filterIsInstance<LocalInspectionToolWrapper>()
      .filter { it.id !in excludeToolIds && profile.isToolEnabled(HighlightDisplayKey.find(it.shortName), file) }
    if (wrappers.isEmpty()) return emptyList()

    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
    val perTool = InspectionEngine.inspectEx(
      wrappers, file, lineRange, lineRange,
      /* isOnTheFly = */ false, /* inspectInjectedPsi = */ false, /* ignoreSuppressedElements = */ false,
      indicator, PairProcessor.alwaysTrue(),
    )

    val codes = LinkedHashSet<String>()
    for ((wrapper, descriptors) in perTool) {
      for (descriptor in descriptors) {
        if (descriptor.lineNumber != lineNumber) continue
        val group = descriptor.problemGroup
        if (group is PyTypeCheckerSuppressableProblemGroup) {
          codes.add(group.codeId)
        }
        else {
          val id = wrapper.id
          codes.add(PySuppressionUtil.toSuppressionCode(id) ?: id)
        }
      }
    }
    return codes.toList()
  }
}
