// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.flavor
import com.intellij.python.sdk.backend.requirementsPath
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.ANALYZE_ENTRIES_IN_REQUIREMENTS_FILE_FAILED
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.CREATE_REQUIREMENTS_FILE_FAILED
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.NO_INTERPRETER_CONFIGURED
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.SOME_REQUIREMENTS_FROM_BASE_FILES_WERE_NOT_UPDATED
import org.jetbrains.annotations.ApiStatus

private val PYTHON_EXTENSIONS = listOf("py", "ipynb")

private fun pythonFileTypes(): List<FileType> {
  val fileTypeManager = FileTypeManager.getInstance()
  return PYTHON_EXTENSIONS.map { fileTypeManager.getFileTypeByExtension(it) }.filter { it !== UnknownFileType.INSTANCE }
}

/**
 * Holder class for generated requirements.
 * @param currentFileOutput content of existing requirements file, if any, with added missing entries
 * @param baseFilesOutput content of base files, referenced from the original file
 * @param unhandledLines lines that we failed to analyze
 * @param unchangedInBaseFiles packages with different versions to notify the user about if modification of base files is not allowed
 */
@ApiStatus.Internal

data class PyRequirementsAnalysisResult(
  val currentFileOutput: List<String>,
  val baseFilesOutput: Map<VirtualFile, List<String>>,
  val unhandledLines: List<String>,
  val unchangedInBaseFiles: List<String>,
) {
  companion object {
    fun empty(): PyRequirementsAnalysisResult = PyRequirementsAnalysisResult(emptyList(), emptyMap(), emptyList(), emptyList())
  }

  fun withImportedPackages(
    importedPackages: MutableMap<String, PythonPackage>,
    settings: PyPackageRequirementsSettings,
  ): PyRequirementsAnalysisResult {
    val newCurrentFile = currentFileOutput + importedPackages.values.map {
      if (settings.specifyVersion) "${it.presentableName}${settings.versionSpecifier.separator}${it.version}" else it.presentableName
    }
    return PyRequirementsAnalysisResult(newCurrentFile, baseFilesOutput, unhandledLines, unchangedInBaseFiles)
  }
}

private suspend fun collectImports(module: Module, psiManager: PsiManager): Set<String> {
  val moduleScope = module.moduleContentScope

  val filesToProcess = readAction {
    pythonFileTypes().flatMap { FileTypeIndex.getFiles(it, moduleScope) }
  }

  // Process each file in its own short read action
  val imported = mutableSetOf<String>()
  for (virtualFile in filesToProcess) {
    readAction {
      val pyFile = psiManager.findFile(virtualFile)?.viewProvider?.allFiles?.firstNotNullOfOrNull { it as? PyFile } ?: return@readAction
      addImports(pyFile, imported)
    }
  }

  return imported
}

internal suspend fun syncWithImports(module: Module) {
  val project = module.project
  val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Sync Python requirements")
  val interpreter = module.asPyProject()?.getInterpreter()
  if (interpreter == null) {
    val configureSdkAction = NotificationAction.createSimpleExpiring(PySdkBundle.message("python.configure.interpreter.action")) {
      PySdkPopupFactory.createAndShow(module)
    }
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.ERROR,
      text = PyBundle.message("python.requirements.error.no.interpreter"),
      project = project,
      action = configureSdkAction,
      displayId = NO_INTERPRETER_CONFIGURED
    )
    return
  }
  val settings = PyPackageRequirementsSettings.getInstance(module)

  if (!ApplicationManager.getApplication().isUnitTestMode) {
    val proceed = withContext(Dispatchers.EDT) { showSyncSettingsDialog(project, settings, interpreter) }
    if (!proceed) return
  }

  val requirementsFile = PyPackageUtil.findRequirementsTxt(module) ?: edtWriteAction {
    PythonRequirementTxtSdkUtils.createRequirementsTxtPath(module, interpreter)
  }

  if (requirementsFile == null) {
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.WARNING,
      text = PyBundle.message("python.requirements.error.create.requirements.file"),
      project = project,
      displayId = CREATE_REQUIREMENTS_FILE_FAILED
    )
    return
  }

  val matchResult = withBackgroundProgress(project, PyBundle.message("python.requirements.analyzing.imports.title")) {
    prepareRequirementsText(module, interpreter, settings)
  }

  writeCommandAction(project, PyBundle.message("python.requirements.action.name")) {
    val documentManager = FileDocumentManager.getInstance()
    documentManager.getDocument(requirementsFile)!!.setText(matchResult.currentFileOutput.joinToString("\n"))
    matchResult.baseFilesOutput.forEach { (file, content) ->
      documentManager.getDocument(file)!!.setText(content.joinToString("\n"))
    }
  }
  withContext(Dispatchers.EDT) {
    PsiManager.getInstance(project).findFile(requirementsFile)?.navigate(true)
  }

  if (matchResult.unhandledLines.isNotEmpty()) {
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.WARNING,
      text = PyBundle.message("python.requirements.warning.unhandled.lines", matchResult.unhandledLines.joinToString(", ")),
      project = project,
      displayId = ANALYZE_ENTRIES_IN_REQUIREMENTS_FILE_FAILED
    )
  }
  if (matchResult.unchangedInBaseFiles.isNotEmpty()) {
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.INFORMATION,
      text = PyBundle.message("python.requirements.info.file.ref.dropped", matchResult.unchangedInBaseFiles.joinToString(", ")),
      project = project,
      displayId = SOME_REQUIREMENTS_FROM_BASE_FILES_WERE_NOT_UPDATED
    )
  }
}

private fun showNotification(
  notificationGroup: NotificationGroup,
  type: NotificationType,
  @NlsContexts.NotificationContent text: String,
  project: Project,
  action: NotificationAction? = null,
  displayId: String,
) {
  val notification =
    notificationGroup.createNotification(PyBundle.message("python.requirements.balloon"), text, type).setDisplayId(displayId)
  if (action != null) notification.addAction(action)
  notification.notify(project)
}


private suspend fun prepareRequirementsText(
  module: Module,
  interpreter: PythonInterpreter,
  settings: PyPackageRequirementsSettings,
): PyRequirementsAnalysisResult {
  val packageManager = PythonPackageManager.forPythonInterpreter(module.project, interpreter)
  val psiManager = PsiManager.getInstance(module.project)
  val imports = collectImports(module, psiManager)
  val installedPackages = packageManager.listInstalledPackages()

  val installedByName = installedPackages.associateBy { it.name }
  val importedPackages = imports.flatMap { name ->
    val normalized = PyPackageName.normalizePackageName(name)
    val alias = PyPsiPackageUtil.moduleToPackageName(name, default = "")
    listOfNotNull(installedByName[normalized], if (alias != normalized) installedByName[alias] else null)
  }.associateByTo(mutableMapOf()) { it.name }

  val existingResult = packageManager.getRootDependenciesFile()?.virtualFile?.let { requirementsFile ->
    readAction {
      psiManager.findFile(requirementsFile)?.let { psiFile ->
        PyRequirementsFileVisitor(importedPackages, settings).visitRequirementsFile(psiFile)
      }
    }
  } ?: PyRequirementsAnalysisResult.empty()

  return existingResult.withImportedPackages(importedPackages, settings)
}

@RequiresEdt
private fun showSyncSettingsDialog(project: Project, settings: PyPackageRequirementsSettings, interpreter: PythonInterpreter): Boolean {
  val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
  val panel = panel {
    row(PyBundle.message("form.integrated.tools.package.requirements.file")) {
      textFieldWithBrowseButton(fileChooserDescriptor = descriptor)
        .bindText({
                    interpreter.requirementsPath?.toString() ?: ""
                  }, { stringPath ->
                    // Goes through a modificator rather than writing to the committed data: an in-place edit leaves the
                    // stored entity behind the bridge, which the workspace model reports as a mismatch (PY-82614).
                    PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(
                      project, interpreter.getSdkAPI(), stringPath.ifBlank { null }?.toNioPathOrNull()
                    )
                  })
        .align(AlignX.FILL)
        .focused()
    }
    row(PyBundle.message("python.requirements.version.label")) {
      comboBox(PyRequirementsVersionSpecifierType.entries)
        .bindItem(settings::getVersionSpecifier, settings::setVersionSpecifier)
        .align(AlignX.FILL)
    }
    row {
      checkBox(PyBundle.message("python.requirements.remove.unused"))
        .bindSelected(settings::getRemoveUnused, settings::setRemoveUnused)
    }
    row {
      checkBox(PyBundle.message("python.requirements.modify.base.files"))
        .bindSelected(settings::getModifyBaseFiles, settings::setModifyBaseFiles)
    }
    row {
      checkBox(PyBundle.message("python.requirements.keep.matching.specifier"))
        .bindSelected(settings::getKeepMatchingSpecifier, settings::setKeepMatchingSpecifier)
    }
  }
  val dialog = dialog(title = PyBundle.message("python.requirements.action.name"),
                      panel = panel,
                      resizable = true,
                      project = project)


  return dialog.showAndGet()
}

private fun addImports(file: PyFile, imported: MutableSet<String>) {
  (file.importTargets.asSequence().mapNotNull { it.importedQName?.firstComponent } +
   file.fromImports.asSequence().mapNotNull { it.importSourceQName?.firstComponent })
    .forEach { imported.add(it) }
}
