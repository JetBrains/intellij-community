package com.intellij.refactoring.detector.semantic.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.CombinedDiffRequest
import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.refactoring.detector.RefactoringDetectorBundle
import org.jetbrains.research.kotlinrminer.ide.KotlinRMiner
import org.jetbrains.research.refactorinsight.kotlin.impl.data.KotlinRefactoringBuilder

class SemanticDiffRefactoringProcessor : DiffExtension() {

  override fun onViewerCreated(viewer: DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return
    val combinedDiffViewer = viewer as? CombinedDiffViewer ?: return
    val combinedDiffRequest = request as? CombinedDiffRequest ?: return

    val childRequests = combinedDiffRequest.getChildRequests()
    val changes = childRequests.mapNotNull { it.request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) }

    runBackgroundableTask(RefactoringDetectorBundle.message("progress.find.refactoring.title")) {
      val refactorings = runReadAction { KotlinRMiner.detectRefactorings(project, changes) }
      val refactoringEntry = KotlinRefactoringBuilder.convertRefactorings(refactorings)

      context.putUserData(REFACTORING_ENTRY, refactoringEntry)

      runInEdt { combinedDiffViewer.rediff() }
    }
  }
}
