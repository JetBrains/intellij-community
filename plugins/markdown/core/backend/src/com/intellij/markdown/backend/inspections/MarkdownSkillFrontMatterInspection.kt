package com.intellij.markdown.backend.inspections

import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.lang.injection.InjectedLanguageManager
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeaderContent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownSkillFrontMatterInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object: PsiElementVisitor() {
    override fun visitFile(file: PsiFile) {
      if (file !is MarkdownFile || file.name != SKILL_FILE_NAME) return

      val header = file.firstChild as? MarkdownFrontMatterHeader
      if (header == null) {
        holder.registerProblem(
          file, MarkdownBundle.message("markdown.skill.front.matter.missing"),
          AddFrontMatterFix(buildSkillName(file))
        )
        return
      }

      val content = header.children.filterIsInstance<MarkdownFrontMatterHeaderContent>().firstOrNull() ?: return
      checkName(content, holder, file)
      checkDescription(content, holder)
    }
  }

  private fun checkName(content: MarkdownFrontMatterHeaderContent, holder: ProblemsHolder, file: PsiFile) {
    val field = findField(content, NAME_FIELD)
    if (field == null) {
      holder.registerProblem(
        content, MarkdownBundle.message("markdown.skill.name.missing"),
        InsertMandatoryFieldFix(NAME_FIELD, buildSkillName(file))
      )
      return
    }

    val value = field.value.removeSurrounding("\"").removeSurrounding("'")
    if (!isValidName(value)) {
      holder.registerProblem(
        content, field.rangeInContent,
        MarkdownBundle.message("markdown.skill.name.invalid"),
        UpdateNameFix(buildSkillName(file))
      )
    }
  }

  private fun checkDescription(content: MarkdownFrontMatterHeaderContent, holder: ProblemsHolder) {
    val field = findField(content, DESCRIPTION_FIELD)
    if (field == null) {
      holder.registerProblem(
        content,
        MarkdownBundle.message("markdown.skill.description.missing"),
        InsertMandatoryFieldFix(DESCRIPTION_FIELD, "", moveCaret = true)
      )
      return
    }

    val value = field.value.removeSurrounding("\"").removeSurrounding("'")
    val message = when {
      value.isBlank() -> MarkdownBundle.message("markdown.skill.description.empty")
      value.length > DESCRIPTION_MAX_LENGTH -> MarkdownBundle.message("markdown.skill.description.too.long")
      else -> return
    }
    holder.registerProblem(content, field.rangeInContent, message)
  }

  private class AddFrontMatterFix(private val name: String): IntentionAndQuickFixAction() {
    override fun getName(): String = MarkdownBundle.message("markdown.skill.front.matter.add.fix")
    override fun getFamilyName(): String = getName()

    override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
      val hostFile = getHostFile(project, psiFile)
      val hostEditor = editor?.let(InjectedLanguageEditorUtil::getTopLevelEditor)
      val document = hostEditor?.document ?: hostFile.viewProvider.document ?: return
      val frontMatter = "---\nname: $name\ndescription: \n---"
      val emptyFrontMatter = EMPTY_FRONT_MATTER_PATTERN.find(document.charsSequence)
      if (emptyFrontMatter != null) {
        document.replaceString(emptyFrontMatter.range.first, emptyFrontMatter.range.last + 1, frontMatter)
      }
      else {
        document.insertString(0, "$frontMatter\n")
      }
      hostEditor?.caretModel?.moveToOffset(frontMatter.indexOf("description: ") + "description: ".length)
    }
  }

  private class InsertMandatoryFieldFix(
    private val field: String,
    private val value: String,
    private val moveCaret: Boolean = false,
  ): IntentionAndQuickFixAction() {
    override fun getName(): String = MarkdownBundle.message("markdown.skill.field.insert.fix", field)
    override fun getFamilyName(): String = getName()

    override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
      val hostFile = getHostFile(project, psiFile)
      val content = findFrontMatterContent(hostFile) ?: return
      val hostEditor = editor?.let(InjectedLanguageEditorUtil::getTopLevelEditor)
      val document = hostEditor?.document ?: hostFile.viewProvider.document ?: return
      val line = "$field: $value"
      val nameField = findField(content, NAME_FIELD)
      val insertAfterName = field == DESCRIPTION_FIELD && nameField != null
      val relativeOffset = if (insertAfterName) nameField.rangeInContent.endOffset else 0
      val hasFollowingNewline = content.text.getOrNull(relativeOffset) == '\n'
      val insertionOffset = content.textRange.startOffset + relativeOffset + if (hasFollowingNewline) 1 else 0
      val insertion = when {
        insertAfterName && hasFollowingNewline -> "$line\n"
        insertAfterName -> "\n$line"
        else -> "$line\n"
      }
      document.insertString(insertionOffset, insertion)
      if (moveCaret) hostEditor?.caretModel?.moveToOffset(insertionOffset + insertion.indexOf(line) + line.length)
    }
  }

  private class UpdateNameFix(private val name: String): IntentionAndQuickFixAction() {
    override fun getName(): String = MarkdownBundle.message("markdown.skill.name.update.fix", name)
    override fun getFamilyName(): String = getName()

    override fun applyFix(project: Project, psiFile: PsiFile, editor: Editor?) {
      val hostFile = getHostFile(project, psiFile)
      val content = findFrontMatterContent(hostFile) ?: return
      val field = findField(content, NAME_FIELD) ?: return
      val hostEditor = editor?.let(InjectedLanguageEditorUtil::getTopLevelEditor)
      val document = hostEditor?.document ?: hostFile.viewProvider.document ?: return
      val range = field.rangeInContent.shiftRight(content.textRange.startOffset)
      val quote = field.value.firstOrNull()?.takeIf { field.value.length >= 2 && (it == '\'' || it == '"') && field.value.last() == it }
      val replacement = if (quote == null) name else "$quote$name$quote"
      document.replaceString(range.startOffset, range.endOffset, "$NAME_FIELD: $replacement")
    }
  }

}

private const val SKILL_FILE_NAME = "SKILL.md"
private const val NAME_FIELD = "name"
private const val DESCRIPTION_FIELD = "description"
private const val DESCRIPTION_MAX_LENGTH = 1024
private val EMPTY_FRONT_MATTER_PATTERN = Regex("\\A---[ \\t]*\\r?\\n---[ \\t]*(?=\\r?\\n|\\z)")
private val FIELD_PATTERN = Regex("(?m)^[ \\t]*(name|description)[ \\t]*:[ \\t]*(.*)$")
private val INVALID_NAME_CHARACTERS_PATTERN = Regex("[^a-z0-9]+")
private val NAME_PATTERN = Regex("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?")

private data class Field(val value: String, val rangeInContent: TextRange)

private fun findField(content: MarkdownFrontMatterHeaderContent, fieldName: String): Field? {
  val match = FIELD_PATTERN.findAll(content.text).firstOrNull { it.groupValues[1] == fieldName } ?: return null
  return Field(match.groupValues[2].trim(), TextRange(match.range.first, match.range.last + 1))
}

private fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)

private fun getHostFile(project: Project, file: PsiFile): PsiFile =
  InjectedLanguageManager.getInstance(project).getTopLevelFile(file)

private fun findFrontMatterContent(file: PsiFile): MarkdownFrontMatterHeaderContent? {
  val header = file.firstChild as? MarkdownFrontMatterHeader ?: return null
  return header.children.filterIsInstance<MarkdownFrontMatterHeaderContent>().firstOrNull()
}

private fun buildSkillName(file: PsiFile): String {
  val name = file.virtualFile.parent?.name
    ?.lowercase()
    ?.replace(INVALID_NAME_CHARACTERS_PATTERN, "-")
    ?.take(64)
    ?.trim('-')
    .orEmpty()
  return name.ifEmpty { "skill" }
}
