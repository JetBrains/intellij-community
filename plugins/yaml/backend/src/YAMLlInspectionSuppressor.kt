// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.asSafely
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.util.regex.Pattern

internal class YAMLlInspectionSuppressor : InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (findKeyNoinspectionComment(element, toolId) != null) return true
    if (findFileNoinspectionComment(element, toolId) != null) return true
    return false
  }


  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
    arrayOf(YAMLSuppressKeyQuickFix(toolId), YAMLSuppressFileQuickFix(toolId))
}

private val SUPPRESS_IN_LINE_COMMENT_PATTERN: Pattern = Pattern.compile("#" + SuppressionUtil.COMMON_SUPPRESS_REGEXP)
private val SUPPRESS_IN_FILE_COMMENT_PATTERN: Pattern =
  Pattern.compile("#" + SuppressionUtil.FILE_PREFIX + SuppressionUtil.COMMON_SUPPRESS_REGEXP)

private fun findKeyNoinspectionComment(element: PsiElement, toolId: String?): PsiComment? {
  return element.parentsOfType<YAMLKeyValue>().firstNotNullOfOrNull { yamlKeyValue ->
    findNoinspectionCommentForKeyValue(yamlKeyValue, toolId)
  }
}

private fun findNoinspectionCommentForKeyValue(yamlKeyValue: YAMLKeyValue, toolId: String?): PsiComment? {
  for (comment in findCommentsBackwards(yamlKeyValue)) {
    val text = comment.text ?: continue
    val matcher = SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text)
    if (!matcher.matches()) continue
    if (toolId == null || SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId))
      return comment
  }
  return null
}

private fun findFileNoinspectionComment(element: PsiElement, toolId: String?): PsiComment? {
  for (child in element.containingFile.children) {
    if (child !is PsiComment) continue
    val text = child.text
    val matcher = SUPPRESS_IN_FILE_COMMENT_PATTERN.matcher(text)
    if (!matcher.matches()) continue
    if (toolId == null || SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId))
      return child
  }
  return null
}

private fun findCommentsBackwards(psiElement: PsiElement): Sequence<PsiComment> {
  val prevSibling = psiElement.prevSibling ?: psiElement.parent.prevSibling ?: return emptySequence()
  return when {
    prevSibling is PsiComment -> sequence {
      yield(prevSibling)
      yieldAll(findCommentsBackwards(prevSibling))
    }
    prevSibling is YAMLKeyValue -> emptySequence()
    YAMLElementTypes.SPACE_ELEMENTS.contains(PsiUtilCore.getElementType(prevSibling)) -> findCommentsBackwards(prevSibling)
    else -> emptySequence()
  }
}

private fun createNewCommentByPattern(pattern: Pattern, existingComment: PsiComment, toolId: String): PsiElement {
  val text = existingComment.text
  val matcher = pattern.matcher(text)
  require(matcher.matches())
  val dummyFile = YAMLElementGenerator.getInstance(existingComment.project)
    .createDummyYamlWithText(text.substring(0, matcher.end(1)) + ",$toolId" + text.substring(matcher.end(1)))
  return PsiTreeUtil.getDeepestFirst(dummyFile)
}


private class YAMLSuppressKeyQuickFix(ID: String) : AbstractBatchSuppressByNoInspectionCommentFix(ID, false) {

  private var keyName: String? = null
  override fun getContainer(context: PsiElement?): PsiElement? {
    val parentOfType = context?.parentOfType<YAMLKeyValue>(true)
    // getContainer is called in `isAvailable` and then we use it to update the `getText`
    keyName = parentOfType.asSafely<YAMLKeyValue>()?.keyText
    return parentOfType
  }

  override fun createSuppression(project: Project, element: PsiElement, container: PsiElement) {
    val existingComment = findNoinspectionCommentForKeyValue(container as YAMLKeyValue, null)
    if (existingComment != null) {
      val comment = createNewCommentByPattern(SUPPRESS_IN_LINE_COMMENT_PATTERN, existingComment, myID)
      existingComment.replace(comment)
    }
    else {
      val generator = YAMLElementGenerator.getInstance(project)
      val previous = container.prevSibling
      var before = if (previous?.node?.elementType == YAMLTokenTypes.INDENT) previous else container
      val dummyFile = generator.createDummyYamlWithText("# noinspection ${this.myID}")
      val comment = PsiTreeUtil.getDeepestFirst(dummyFile)
      before = before.parent.addBefore(generator.createEol(), before)
      before.parent.addBefore(comment, before)
    }
  }

  override fun getText(): String = YAMLBundle.message("suppress.inspection.key", myID, keyName)
}

private class YAMLSuppressFileQuickFix(ID: String) :
  AbstractBatchSuppressByNoInspectionCommentFix(ID, false) {

  private var keyName: String? = null
  override fun getContainer(context: PsiElement?): PsiElement? = context?.containingFile?.also {
    // getContainer is called in `isAvailable` and then we use it to update the `getText`
    keyName = it.asSafely<PsiFile>()?.name
  }

  override fun createSuppression(project: Project, element: PsiElement, container: PsiElement) {
    val generator = YAMLElementGenerator.getInstance(project)
    val existingComment = findFileNoinspectionComment(container as PsiFile, null)
    if (existingComment != null) {
      val comment = createNewCommentByPattern(SUPPRESS_IN_FILE_COMMENT_PATTERN, existingComment, myID)
      existingComment.replace(comment)
      DaemonCodeAnalyzer.getInstance(project).restart(container)
    }
    else {
      var before = container.firstChild
      val dummyFile = generator.createDummyYamlWithText("#file: noinspection ${this.myID}")
      val comment = PsiTreeUtil.getDeepestFirst(dummyFile)
      before = before.parent.addBefore(generator.createEol(), before)
      before.parent.addBefore(comment, before)
    }
  }

  override fun getText(): String = YAMLBundle.message("suppress.inspection.file", myID, keyName)
}