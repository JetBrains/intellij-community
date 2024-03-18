package com.intellij.python.grazie

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.ProblemFilterUtil

class PythonProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    val domain = problem.text.domain
    if (domain == TextContent.TextDomain.LITERALS) {
      return problem.fitsGroup(RuleGroup.LITERALS)
    }
    if (domain == TextContent.TextDomain.DOCUMENTATION && seemsDocString(problem.text) &&
        (ProblemFilterUtil.isUndecoratedSingleSentenceIssue(problem) || ProblemFilterUtil.isInitialCasingIssue(problem))) {
      return true
    }
    return false
  }

  private fun seemsDocString(text: TextContent) =
    text.containingFile.viewProvider.contents.subSequence(0, text.textOffsetToFile(0)).trim().endsWith(":")

}