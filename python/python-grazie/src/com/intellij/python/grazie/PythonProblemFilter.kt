package com.intellij.python.grazie

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.ProblemFilterUtil
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl
import java.util.regex.Pattern

private val PY_DOC_PARAM = Pattern.compile("[a-z0-9_]+\\s*:\\s+\\p{L}+( or \\p{L}+)*")

internal class PythonProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    val domain = problem.text.domain
    return domain == TextContent.TextDomain.DOCUMENTATION &&
           (ProblemFilterUtil.isUndecoratedSingleSentenceIssue(problem) ||
            ProblemFilterUtil.isInitialCasingIssue(problem) ||
            problem.fitsGroup(RuleGroup(RuleGroup.UNDECORATED_SENTENCE_SEPARATION)) ||
            fitsPyDocParam(problem))
  }

  private fun fitsPyDocParam(problem: TextProblem): Boolean {
    val text = problem.text
    //todo remove after https://youtrack.jetbrains.com/issue/PY-59061 is fixed
    if (text.domain == TextContent.TextDomain.DOCUMENTATION && text.getCommonParent().getParent() is PyStringLiteralExpressionImpl) {
      val matcher = PY_DOC_PARAM.matcher(text)
      while (matcher.find()) {
        if (problem.highlightRanges.any { it.intersects(matcher.start(), matcher.end()) }) {
          return true
        }
      }
    }
    return false
  }
}