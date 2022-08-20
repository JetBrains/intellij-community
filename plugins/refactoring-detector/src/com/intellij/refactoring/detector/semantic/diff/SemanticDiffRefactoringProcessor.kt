package com.intellij.refactoring.detector.semantic.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.COMBINED_DIFF_MODEL
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.refactoring.detector.RefactoringDetectorBundle
import org.jetbrains.research.kotlinrminer.ide.KotlinRMiner
import org.jetbrains.research.refactorinsight.kotlin.impl.data.KotlinRefactoringBuilder

class SemanticDiffRefactoringProcessor : DiffExtension() {

  override fun onViewerCreated(viewer: DiffViewer, context: DiffContext, request: DiffRequest) {
    if (!Registry.`is`("enable.semantic.inlays.in.combined.diff")) return

    val project = context.project ?: return
    val combinedDiffViewer = context.getUserData(COMBINED_DIFF_VIEWER_KEY) ?: return
    val combinedDiffModel = context.getUserData(COMBINED_DIFF_MODEL) ?: return

    val changes =
      combinedDiffModel.requests.values
        .asSequence()
        .filterIsInstance<ChangeDiffRequestProducer>()
        .map(ChangeDiffRequestProducer::getChange)
        .toList()

    if (changes.isEmpty()) return

    runBackgroundableTask(RefactoringDetectorBundle.message("progress.find.refactoring.title")) {
      val refactorings = runReadAction { KotlinRMiner.detectRefactorings(project, changes) }
      val refactoringEntry = KotlinRefactoringBuilder.convertRefactorings(refactorings)

      context.putUserData(REFACTORING_ENTRY, refactoringEntry)

      runInEdt { combinedDiffViewer.rediff() }
    }
  }
}
