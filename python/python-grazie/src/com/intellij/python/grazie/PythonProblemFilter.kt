package com.intellij.python.grazie

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.ProblemFilterUtil

class PythonProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    val domain = problem.text.domain
    return domain == TextContent.TextDomain.DOCUMENTATION &&
           (ProblemFilterUtil.isUndecoratedSingleSentenceIssue(problem) ||
            ProblemFilterUtil.isInitialCasingIssue(problem) ||
            problem.fitsGroup(RuleGroup(RuleGroup.UNDECORATED_SENTENCE_SEPARATION)))
  }
}