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
import com.intellij.openapi.ui.DialogPanel
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
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils.validateTargetDir
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Experimental
abstract class MarkdownFileActionsBaseDialog(
  protected val project: Project,
  protected val suggestedFilePath: String,
  protected val targetFile: VirtualFile
) : DialogWrapper(project, true) {
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
        cell {
          fileNameField(growX).withValidationOnApply { validateFileName(it) }.focused()
        }
      }
      createFileTypeField()
      row(MarkdownBundle.message("markdown.import.export.dialog.target.directory.label")) {
        cell {
          targetDirectoryField(growX).withValidationOnApply { validateTargetDir(it) }.focused()
        }
      }
      row {
        val settingsComponent = getSettingsComponents() ?: return@row
        val panel = JPanel(BorderLayout()).apply { add(settingsComponent) }

        component(panel).withValidationOnApply {
          settingsComponent.validateCallbacks.find { it.invoke() != null }?.invoke()
        }
      }
      row {
        cell {
          label(
            MarkdownBundle.message("markdown.import.export.dialog.path.completion.shortcut", shortcutText),
            UIUtil.ComponentStyle.SMALL,
            UIUtil.FontColor.BRIGHTER
          )
        }
      }
    }
  }

  override fun getPreferredFocusedComponent() = fileNameField

  override fun doOKAction() {
    val fileName = fileNameField.text.trim()
    val targetDir = targetDirectoryField.childComponent.text
    val fileUrl = FileUtil.join(targetDir, fileName)
    val srcDirectory = PsiManager.getInstance(project).findFile(targetFile)!!.containingDirectory

    FileChooserUtil.setLastOpenedFile(project, srcDirectory.virtualFile.toNioPath())
    createDirIfNotExist(targetDir)
    RecentsManager.getInstance(project).registerRecentEntry(RECENT_KEYS, targetDir)

    doAction(fileUrl)
    super.doOKAction()
  }

  protected abstract fun doAction(selectedFileUrl: String)

  protected abstract fun getFileNameIfExist(dir: String, fileNameWithoutExtension: String): String?

  private fun createDirIfNotExist(dirPath: String) {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val path = FileUtil.toSystemIndependentName(dirPath)
        DirectoryUtil.mkdirs(PsiManager.getInstance(project), path)
      }
    }, MarkdownBundle.message("markdown.import.export.dialog.create.directory"), null)
  }

  private fun createTargetDirField() {
    targetDirectoryField.apply {
      setTextFieldPreferredWidth(MAX_PATH_LENGTH)

      val resDirRecent = RecentsManager.getInstance(project).getRecentEntries(RECENT_KEYS)
      if (resDirRecent != null) {
        childComponent.history = resDirRecent
      }
      childComponent.text = File(suggestedFilePath).parent

      addBrowseFolderListener(
        MarkdownBundle.message("markdown.import.export.dialog.target.directory"),
        MarkdownBundle.message("markdown.import.export.dialog.target.directory.description"),
        project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT
      )
    }
  }

  private fun createFileNameField() {
    val file = File(suggestedFilePath)
    fileNameField.text = file.nameWithoutExtension

    val endOfNameIdx = file.nameWithoutExtension.length
    if (endOfNameIdx > 0) {
      selectNameWithoutExtension(endOfNameIdx)
    }
  }

  protected open fun LayoutBuilder.createFileTypeField(): Row? = null

  protected open fun getSettingsComponents(): DialogPanel? = null

  private fun selectNameWithoutExtension(dotIdx: Int) {
    ApplicationManager.getApplication().invokeLater {
      val editor = fileNameField.editor
      if (editor != null) {
        editor.selectionModel.setSelection(0, dotIdx)
        editor.caretModel.moveToOffset(dotIdx)
      }
      else {
        fileNameField.selectAll()
      }
    }
  }

  private fun ValidationInfoBuilder.validateFileName(field: EditorTextField): ValidationInfo? {
    val dir = targetDirectoryField.childComponent.text
    val fileName = field.text
    val existedFileName = getFileNameIfExist(dir, fileName)

    return when {
      field.isNull || fileName.isEmpty() -> error(RefactoringBundle.message("no.new.name.specified"))
      !PathUtilRt.isValidFileName(fileName, false) -> error(RefactoringBundle.message("name.is.not.a.valid.file.name"))
      !dir.isNullOrEmpty() && !existedFileName.isNullOrEmpty() -> {
        error(MarkdownBundle.message("markdown.import.export.dialog.file.exist.error", existedFileName))
      }
      else -> null
    }
  }

  companion object {
    const val MAX_PATH_LENGTH = 70
    private const val RECENT_KEYS: @NonNls String = "ImportExportFile.TargetDir.RECENT_KEYS"
  }
}
