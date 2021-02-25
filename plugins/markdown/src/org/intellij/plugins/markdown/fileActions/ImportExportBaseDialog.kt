// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions

import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.EditorTextField
import com.intellij.ui.RecentsManager
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.PathUtilRt
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.jetbrains.annotations.NonNls
import java.io.File
import javax.swing.JComponent
import javax.swing.SwingUtilities

abstract class ImportExportBaseDialog(private val project: Project,
                                      private val suggestedFilePath: String,
                                      private val vFileToImport: VirtualFile) : DialogWrapper(project, true) {

  private val MAX_PATH_LENGTH = 70
  private val RECENT_KEYS: @NonNls String = "ImportExportFile.TargetDir.RECENT_KEYS"

  private val fileNameField = EditorTextField()
  private val targetDirectoryField = TextFieldWithHistoryWithBrowseButton()

  init {
    super.init()

    createFileNameField()
    createTargetDirField()
  }

  override fun createCenterPanel(): JComponent {
    val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION))

    return panel {
      row(MarkdownBundle.message("markdown.import.export.dialog.new.name.label")) {
        fileNameField(growX).withValidationOnApply { validateFileName(it) }.focused()
      }
      row(MarkdownBundle.message("markdown.import.export.dialog.target.directory.label")) {
        targetDirectoryField(growX).withValidationOnApply { validateTargetDir(it) }.focused()
      }
      row {
        label(
          MarkdownBundle.message("markdown.import.export.dialog.path.completion.shortcut", shortcutText),
          UIUtil.ComponentStyle.SMALL,
          UIUtil.FontColor.BRIGHTER
        )
      }
    }
  }

  override fun getPreferredFocusedComponent() = fileNameField

  override fun doOKAction() {
    val fileName = getFileNameFromField()
    val targetDir = targetDirectoryField.childComponent.text
    val fileUrl = "$targetDir/$fileName"
    val srcDirectory = PsiManager.getInstance(project).findFile(vFileToImport)!!.containingDirectory

    FileChooserUtil.setLastOpenedFile(project, srcDirectory.virtualFile.toNioPath())
    createDirIfNotExist(targetDir)
    RecentsManager.getInstance(project).registerRecentEntry(RECENT_KEYS, targetDir)

    doAction(fileUrl)
    super.doOKAction()
  }

  protected abstract fun doAction(selectedFileUrl: String)

  private fun getFileNameFromField(): String? = if (!fileNameField.isNull) fileNameField.text.trim() else null

  private fun createDirIfNotExist(dirPath: String) {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val path = FileUtil.toSystemIndependentName(dirPath)
        DirectoryUtil.mkdirs(PsiManager.getInstance(project), path)
      }
    }, MarkdownBundle.message("markdown.import.export.dialog.create.directory"), null)
  }

  private fun createTargetDirField() {
    targetDirectoryField.setTextFieldPreferredWidth(MAX_PATH_LENGTH)

    val resDirRecent = RecentsManager.getInstance(project).getRecentEntries(RECENT_KEYS)
    if (resDirRecent != null) {
      targetDirectoryField.childComponent.history = resDirRecent
    }
    targetDirectoryField.childComponent.text = File(suggestedFilePath).parent

    targetDirectoryField.addBrowseFolderListener(
      MarkdownBundle.message("markdown.import.export.dialog.target.directory"),
      MarkdownBundle.message("markdown.import.export.dialog.target.directory.description"),
      project,
      FileChooserDescriptorFactory.createSingleFolderDescriptor(),
      TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT
    )
  }

  private fun createFileNameField() {
    val fileName = File(suggestedFilePath).name
    fileNameField.text = fileName

    val dotIdx = fileName.lastIndexOf('.')
    if (dotIdx > 0) {
      selectNameWithoutExtension(dotIdx)
    }
  }

  private fun selectNameWithoutExtension(dotIdx: Int) {
    val selectRunnable = Runnable {
      val editor = fileNameField.editor
      if (editor != null) {
        editor.selectionModel.setSelection(0, dotIdx)
        editor.caretModel.moveToOffset(dotIdx)
      }
      else {
        fileNameField.selectAll()
      }
    }
    SwingUtilities.invokeLater(selectRunnable)
  }

  private fun ValidationInfoBuilder.validateFileName(field: EditorTextField): ValidationInfo? {
    val dir = targetDirectoryField.childComponent.text

    return when {
      field.isNull || field.text.isEmpty() -> error(RefactoringBundle.message("no.new.name.specified"))
      !PathUtilRt.isValidFileName(field.text, false) -> error(RefactoringBundle.message("name.is.not.a.valid.file.name"))
      !dir.isNullOrEmpty() && File("$dir/${field.text}").exists() -> error(MarkdownBundle.message("markdown.import.export.dialog.file.exist.error", field.text))
      else -> null
    }
  }

  private fun ValidationInfoBuilder.validateTargetDir(field: TextFieldWithHistoryWithBrowseButton) =
    when {
      field.childComponent.text.isNullOrEmpty() -> error(RefactoringBundle.message("no.target.directory.specified"))
      else -> null
    }
}
