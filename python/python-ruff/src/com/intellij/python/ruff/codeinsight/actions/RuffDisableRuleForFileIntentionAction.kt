// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight.actions

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.python.pytools.statistics.PyToolUsagesCollector.Helper.logDisableRule
import com.intellij.python.ruff.RuffBundle
import com.intellij.util.IncorrectOperationException

/**
 * An intention action that disables a Ruff rule for the entire file by adding a comment
 * at the top of the file: # ruff: noqa: RULE_CODE
 */
class RuffDisableRuleForFileIntentionAction(private val ruleCode: String) : BaseIntentionAction() {
  init {
    text = RuffBundle.message("intention.name.disable.for.this.file")
  }

  @IntentionFamilyName
  override fun getFamilyName(): String = RuffBundle.message("intention.family.name.disable.ruff.rule.for.entire.file")

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    return true
  }

  @Throws(IncorrectOperationException::class)
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    logDisableRule(project, true)

    val document = editor.document
    
    val firstLineText = if (document.lineCount > 0) document.getText(TextRange(0, document.getLineEndOffset(0))) else ""
    
    val noqaCommentRegex = """#\s*ruff:\s*noqa:\s*(.*?)(#|$)""".toRegex()
    val matchResult = noqaCommentRegex.find(firstLineText)
    
    if (matchResult != null) {
      val existingRuleCodes = matchResult.groupValues[1].split(",").map { it.trim() }
      
      if (ruleCode !in existingRuleCodes) {
        val updatedRuleCodes = (existingRuleCodes + ruleCode).joinToString(", ")
        val updatedComment = "# ruff: noqa: $updatedRuleCodes"
        
        WriteCommandAction.runWriteCommandAction(project, RuffBundle.message("command.name.append.ruff.rule.to.noqa.comment"), null, {
          document.replaceString(matchResult.range.first, matchResult.range.last + 1, updatedComment)
        }, file)
      }
    } else {
      val comment = "# ruff: noqa: $ruleCode\n"
      
      WriteCommandAction.runWriteCommandAction(project, RuffBundle.message("command.name.add.ruff.noqa.comment"), null, {
        document.insertString(0, comment)
      }, file)
    }
  }
}