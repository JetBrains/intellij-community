// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight.actions

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.statistics.PyToolUsagesCollector
import com.intellij.python.ruff.RuffBundle
import com.intellij.util.IncorrectOperationException
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import javax.swing.Icon

/**
 * An intention action that disables a Ruff rule by adding it to the tool.ruff.lint.ignore array
 * in the pyproject.toml file.
 */
class RuffDisableRuleIntentionAction(private val ruleCode: String) : BaseIntentionAction(), Iconable {
  init {
    text = RuffBundle.message("intention.name.disable.for.this.project")
  }

  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Cancel

  @IntentionFamilyName
  override fun getFamilyName(): String = RuffBundle.message("intention.family.name.disable.ruff.rule")

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    return true
  }

  @Throws(IncorrectOperationException::class)
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    PyToolUsagesCollector.Helper.logDisableRule(project, false)

    val configFile = findRuffConfigFile(project, file)

    val isPyProjectTarget = configFile?.name == PY_PROJECT_TOML
    val sectionName = if (isPyProjectTarget) "tool.ruff.lint" else "lint"

    WriteCommandAction.runWriteCommandAction(project, RuffBundle.message("command.name.ruff.disable.rule"), "ruff", {
      if (configFile != null) {
        val psiConfig = PsiManager.getInstance(project).findFile(configFile) as? TomlFile
        if (psiConfig != null) {
          addRuleToIgnoreInSection(psiConfig, ruleCode, sectionName)
          commitAndSave(project, psiConfig)
        }
      } else {
        val parentVFile = file.virtualFile?.parent
        require(parentVFile != null) { "Cannot determine target directory for ruff.toml" }
        val psiDir = PsiManager.getInstance(project).findDirectory(parentVFile)
        require(psiDir != null) { "Cannot find PSI directory for target location" }
        val newPsiFile = PsiFileFactory.getInstance(project).createFileFromText("ruff.toml", TomlFileType, "") as TomlFile
        addRuleToIgnoreInSection(newPsiFile, ruleCode, "lint")
        val added = psiDir.add(newPsiFile)
        val addedPsiFile = (added as? PsiFile) ?: PsiManager.getInstance(project).findFile((added.containingFile?.virtualFile) ?: return@runWriteCommandAction)
        if (addedPsiFile != null) {
          commitAndSave(project, addedPsiFile)
        }
      }
      PyToolUsagesCollector.Helper.logDisableRule(project, false)
    }, file)
  }

  private fun commitAndSave(project: Project, psiFile: PsiFile) {
    val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
    val pdm = PsiDocumentManager.getInstance(project)
    pdm.commitDocument(doc)
    pdm.doPostponedOperationsAndUnblockDocument(doc)
    pdm.commitDocument(doc)
    FileDocumentManager.getInstance().saveDocument(doc)
    psiFile.virtualFile?.refresh(false, false)
  }

  /**
   * Finds the pyproject.toml file in the project.
   * This method first looks for the file in the project root directory,
   * and if not found, searches for it in the entire project using FilenameIndex.
   */
  private fun findRuffConfigFile(project: Project, contextFile: PsiFile?): VirtualFile? {
    val candidateDirs = mutableListOf<VirtualFile>()
    contextFile?.virtualFile?.parent?.let { candidateDirs.add(it) }
    project.basePath?.let { base ->
      LocalFileSystem.getInstance().findFileByPath(base)?.let { baseDir ->
        if (candidateDirs.none { it.path == baseDir.path }) candidateDirs.add(baseDir)
      }
    }

    val names = listOf(".ruff.toml", "ruff.toml", PY_PROJECT_TOML)
    for (dir in candidateDirs) {
      for (name in names) {
        dir.findChild(name)?.let { return it }
      }
    }
    return null
  }

  private fun addRuleToIgnoreInSection(tomlFile: TomlFile, ruleCode: String, sectionName: String) {
    val factory = TomlPsiFactory(tomlFile.project)

    fun findTable(section: String): TomlTable? {
      val tables = PsiTreeUtil.findChildrenOfType(tomlFile, TomlTable::class.java)
      return tables.firstOrNull { table ->
        val key = table.header.key ?: return@firstOrNull false
        val path = key.segments.joinToString(".") { it.name ?: "" }
        path == section
      }
    }

    var table = findTable(sectionName)
    if (table == null) {
      val lastDot = sectionName.lastIndexOf('.')
      val parentSection = if (lastDot != -1) sectionName.take(lastDot) else null
      val parentTable = parentSection?.let { findTable(it) }

      fun addAtEnd(): TomlTable {
        if (tomlFile.text.isNotEmpty()) {
          val twoNl = factory.createWhitespace("\n\n")
          val last = tomlFile.lastChild
          if (last is PsiWhiteSpace) {
            last.replace(twoNl)
          } else {
            tomlFile.add(twoNl)
          }
        }
        return tomlFile.add(factory.createTable(sectionName)) as TomlTable
      }

      table = if (parentTable != null) {
        if (parentTable.entries.isEmpty()) {
          // Ensure exactly one newline between headers: normalize whitespace after parent header to a single \n
          val afterParent = parentTable.nextSibling
          val oneNl = factory.createWhitespace("\n")
          val ws = if (afterParent is PsiWhiteSpace) {
            afterParent.replace(oneNl)
          } else {
            tomlFile.addAfter(oneNl, parentTable)
          }
          val t = tomlFile.addAfter(factory.createTable(sectionName), ws) as TomlTable
          t
        } else {
          // Ensure exactly one empty line between sections: normalize whitespace after parent block to two \n
          var sibling = parentTable.nextSibling
          var lastInParentBlock: PsiElement = parentTable
          while (sibling != null && sibling !is TomlTable) {
            lastInParentBlock = sibling
            sibling = sibling.nextSibling
          }
          val twoNl = factory.createWhitespace("\n\n")
          val ws = if (lastInParentBlock is PsiWhiteSpace) {
            lastInParentBlock.replace(twoNl)
          } else {
            tomlFile.addAfter(twoNl, lastInParentBlock)
          }
          if (sibling is TomlTable) {
            val t = tomlFile.addBefore(factory.createTable(sectionName), sibling) as TomlTable
            t
          } else {
            val t = tomlFile.addAfter(factory.createTable(sectionName), ws) as TomlTable
            t
          }
        }
      } else {
        addAtEnd()
      }
    }

    fun findIgnoreEntry(tbl: TomlTable): TomlKeyValue? =
      tbl.entries.firstOrNull { kv ->
        val segments = kv.key.segments
        segments.size == 1 && segments[0].name == "ignore"
      }

    val existingIgnore = findIgnoreEntry(table)

    fun replaceIgnore(target: TomlKeyValue, newValueText: String) {
      val newKv = factory.createKeyValue("ignore", newValueText)
      val replaced = target.replace(newKv) as TomlKeyValue
      val ws = replaced.nextSibling
      if (ws is PsiWhiteSpace) {
        ws.replace(factory.createNewline())
      }
    }

    fun getStringValue(lit: TomlLiteral): String? {
      val k = lit.kind
      return if (k is TomlLiteralKind.String) k.value else null
    }

    fun buildArrayText(items: List<String>, multiline: Boolean): String {
      return if (!multiline && items.size <= 1) {
        items.firstOrNull()?.let { "[\"$it\"]" } ?: "[]"
      } else {
        val body = items.joinToString(",\n    ") { "\"$it\"" }
        "[\n    $body,\n]"
      }
    }

    if (existingIgnore != null) {
      val value = existingIgnore.value
      when (value) {
        is TomlArray -> {
          val existingNames = value.elements.mapNotNull { (it as? TomlLiteral)?.let { lit -> getStringValue(lit) } }.toMutableList()
          if (existingNames.contains(ruleCode)) return
          val multiline = existingNames.isNotEmpty()
          val newValueText = buildArrayText(existingNames + ruleCode, multiline)
          replaceIgnore(existingIgnore, newValueText)
        }
        is TomlLiteral -> {
          val existing = getStringValue(value)
          if (existing == ruleCode) return
          val items = listOfNotNull(existing, ruleCode)
          val newValueText = buildArrayText(items, multiline = items.size > 1)
          replaceIgnore(existingIgnore, newValueText)
        }
        else -> {
          replaceIgnore(existingIgnore, "[\"$ruleCode\"]")
        }
      }
    } else {
      val newKv = factory.createKeyValue("ignore", "[\"$ruleCode\"]")
      val anchor = table.entries.lastOrNull()
      if (anchor != null) {
        val nl = factory.createNewline()
        val insertedNl = table.addAfter(nl, anchor)
        val added = table.addAfter(newKv, insertedNl)
        table.addAfter(factory.createNewline(), added)
      } else {
        val afterHeader = table.firstChild.nextSibling
        val oneNl = factory.createNewline()
        val wsAnchor = if (afterHeader is PsiWhiteSpace) {
          afterHeader.replace(oneNl)
        } else {
          table.addAfter(oneNl, table.firstChild)
        }
        table.addAfter(newKv, wsAnchor)
      }
    }

    val last = table.lastChild
    val nl = when (table.nextSibling) {
      is PsiWhiteSpace -> return
      null -> factory.createNewline()
      else -> factory.createWhitespace("\n\n")
    }
    if (last is PsiWhiteSpace) last.replace(nl) else table.add(nl)
  }
}
