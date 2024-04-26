// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions

import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.ui.TextComponentAccessors
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.SkipOverwriteChoice
import com.intellij.ui.EditorTextField
import com.intellij.ui.RecentsManager
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtilRt
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.utils.MarkdownImportExportUtils.validateTargetDir
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.File
import javax.swing.JComponent

@ApiStatus.Experimental
internal abstract class MarkdownFileActionsBaseDialog(
  protected val project: Project,
  protected val suggestedFilePath: String,
  protected val file: VirtualFile,
) : DialogWrapper(project, true) {
  private val fileNameField = EditorTextField()
  private val targetDirectoryField = TextFieldWithHistoryWithBrowseButton()

  init {
    super.init()

    createFileNameField()
    createTargetDirField()
  }

  override fun createCenterPanel(): JComponent = panel {
    row(MarkdownBundle.message("markdown.import.export.dialog.new.name.label")) {
      cell(fileNameField)
        .align(AlignX.FILL)
        .validationOnApply { validateFileName(it) }.focused()
    }
    createFileTypeField()
    row(MarkdownBundle.message("markdown.import.export.dialog.target.directory.label")) {
      cell(targetDirectoryField)
        .align(AlignX.FILL)
        .validationOnApply { validateTargetDir(it) }
    }
    createSettingsComponents()
  }.apply {
    border = JBUI.Borders.emptyLeft(3) // Reserve space for Html export provider, so there is no horizontal shift when it's shown
  }

  override fun getPreferredFocusedComponent() = fileNameField

  override fun doOKAction() {
    val fileName = fileNameField.text.trim()
    val targetDirPath = targetDirectoryField.childComponent.text
    val fileUrl = FileUtil.join(targetDirPath, fileName)
    val srcDirectory = PsiManager.getInstance(project).findFile(file)!!.containingDirectory

    val existedFileName = getFileNameIfExist(targetDirPath, fileName)
    if (existedFileName != null) {
      val psiTargetDir = getPsiTargetDirectory(targetDirPath) ?: return
      val userChoice = SkipOverwriteChoice.askUser(psiTargetDir, fileName, title, false)

      if (userChoice == SkipOverwriteChoice.OVERWRITE) {
        val pathname = FileUtil.join(targetDirPath, existedFileName)
        val existedFilePath = File(pathname).toPath()
        val virtualFile = VfsUtil.findFile(existedFilePath, true)!!
        ApplicationManager.getApplication().runWriteAction {
          PsiManager.getInstance(project).findFile(virtualFile)!!.delete()
        }

        writeFile(fileUrl, targetDirPath)
      }
    } else {
      writeFile(fileUrl, targetDirPath)
    }

    val path = srcDirectory.virtualFile.fileSystem.getNioPath(srcDirectory.virtualFile)
    if (path != null) {
      FileChooserUtil.setLastOpenedFile(project, path)
    }
    RecentsManager.getInstance(project).registerRecentEntry(RECENT_KEYS, targetDirPath)
    super.doOKAction()
  }

  protected abstract fun doAction(selectedFileUrl: String)

  protected abstract fun getFileNameIfExist(dir: String, fileNameWithoutExtension: String): String?

  private fun createDirIfNotExist(dirPath: String) {
    var targetDirectory: PsiDirectory? = null
    executeCommand(project, MarkdownBundle.message("markdown.import.export.dialog.create.directory"), null) {
      runWriteAction {
        try {
          val path = FileUtil.toSystemIndependentName(dirPath)
          targetDirectory = DirectoryUtil.mkdirs(PsiManager.getInstance(project), path)
        }
        catch (ignored: IncorrectOperationException) {
        }
      }
    }

    if (targetDirectory == null) {
      showErrorDialog(project, RefactoringBundle.message("cannot.create.directory"), RefactoringBundle.message("error.title"))
    }
  }

  private fun createTargetDirField() {
    targetDirectoryField.apply {
      setTextFieldPreferredWidth(MAX_PATH_LENGTH)

      val resDirRecent = RecentsManager.getInstance(project).getRecentEntries(RECENT_KEYS)
      if (resDirRecent != null) {
        childComponent.history = resDirRecent
      }

      val suggestedDir = file.parent.path
      if (resDirRecent == null && childComponent.history.isEmpty()) {
        childComponent.history = listOf(suggestedDir)
      }
      childComponent.text = suggestedDir

      addBrowseFolderListener(
        MarkdownBundle.message("markdown.import.export.dialog.target.directory"),
        MarkdownBundle.message("markdown.import.export.dialog.target.directory.description"),
        project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT
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

  protected open fun Panel.createFileTypeField() {}

  protected open fun Panel.createSettingsComponents() {}

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
    val fileName = field.text

    return when {
      field.isNull || fileName.isEmpty() -> error(RefactoringBundle.message("no.new.name.specified"))
      !PathUtilRt.isValidFileName(fileName, false) -> error(RefactoringBundle.message("name.is.not.a.valid.file.name"))
      else -> null
    }
  }

  private fun getPsiTargetDirectory(pathname: String): PsiDirectory? {
    val dirPath = File(pathname).toPath()
    val vfDirectory = VfsUtil.findFile(dirPath, true)

    return vfDirectory?.let { PsiManager.getInstance(project).findDirectory(it) }
  }

  private fun writeFile(fileUrl: String, targetDirPath: String) {
    createDirIfNotExist(targetDirPath)
    doAction(fileUrl)
  }

  companion object {
    const val MAX_PATH_LENGTH = 70
    private const val RECENT_KEYS: @NonNls String = "ImportExportFile.TargetDir.RECENT_KEYS"
  }
}
