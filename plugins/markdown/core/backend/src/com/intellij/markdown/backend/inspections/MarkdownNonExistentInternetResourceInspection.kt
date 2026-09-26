package com.intellij.markdown.backend.inspections

import com.intellij.codeInspection.LocalInspectionTool

/**
 * This inspections is used to enable/disable checking internet links by external annotator
 * @see MarkdownNonExistentInternetResourcesAnnotator
 */
class MarkdownNonExistentInternetResourceInspection: LocalInspectionTool() {
  override fun getShortName(): String = SHORT_NAME

  companion object {
    const val SHORT_NAME: String = "MarkdownNonExistentInternetResource"
  }
}