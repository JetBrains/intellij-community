package com.intellij.jupyter.ui.test.util.codeinsight

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.sdk.HighlightInfo
import com.intellij.driver.sdk.InspectionProfileManager
import com.intellij.driver.sdk.PsiFile
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Let's keep it internal until a better approach is made
 */
val HighlightInfo.isError: Boolean
  get() = getSeverity().getName() == "ERROR"

val HighlightInfo.isWarning: Boolean
  get() = getSeverity().getName() == "WARNING"

fun Collection<HighlightInfo>.checkHasNoErrors() {
  val errors = filter { it.isError }
  assertTrue(errors.isEmpty()) {
    "Code contains errors: $errors"
  }
}

fun Driver.filterInfosByInspectionProfile(psiFile: PsiFile, infos: Collection<HighlightInfo>, profileName: String): Collection<HighlightInfo> {
  val currentProfile = service<InspectionProfileManager>().getCurrentProfile()
  val targetAttributes = currentProfile.getEditorAttributes(profileName, psiFile)

  return infos.filter {
    val highlighter = it.getHighlighter()
    highlighter != null && highlighter.getTextAttributesKey().compareTo(targetAttributes) == 0
  }
}
