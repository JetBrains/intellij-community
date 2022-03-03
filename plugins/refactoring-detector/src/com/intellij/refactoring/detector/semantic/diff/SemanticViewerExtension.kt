package com.intellij.refactoring.detector.semantic.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.COMBINED_DIFF_PROCESSOR
import com.intellij.diff.tools.util.base.DiffViewerBase

class SemanticViewerExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val fromCombinedDiff = context.getUserData(COMBINED_DIFF_PROCESSOR) != null
    val notFragment = request !is SemanticFragmentDiffRequest

    if (viewer is DiffViewerBase && fromCombinedDiff && notFragment) {
      SemanticInlayModel.install(context, viewer)
    }
  }
}
