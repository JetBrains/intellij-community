package org.jetbrains.yaml.context

import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLPsiElement

private val numberRegex = "\\[\\d+\\]".toRegex()
const val PARSE_DELAY = 1000L

fun fullYamlKey(element: PsiElement?): String {
  return element?.parentOfType<YAMLPsiElement>(true)
    ?.let(YAMLUtil::getConfigFullName)
    ?.replace(numberRegex, "")
    .orEmpty()
}

inline fun <reified T : PsiElement> yamlKeysPatternCondition(keys: List<String>): PatternCondition<PsiElement> =
  object : PatternCondition<PsiElement>(
    "yamlKeysPattern") {
    val regexList = keys.map { it.toRegex() }
    override fun accepts(element: PsiElement, context: ProcessingContext): Boolean {
      return ((element is T)
              && regexList.any { it.matches(StringUtil.newBombedCharSequence(fullYamlKey(element), PARSE_DELAY)) })
    }
  }