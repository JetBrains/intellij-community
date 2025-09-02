// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.ANALYZE_ENTRIES_IN_REQUIREMENTS_FILE_FAILED
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.CREATE_REQUIREMENTS_FILE_FAILED
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.NO_INTERPRETER_CONFIGURED
import com.jetbrains.python.statistics.SyncPythonRequirementsIdsHolder.Companion.SOME_REQUIREMENTS_FROM_BASE_FILES_WERE_NOT_UPDATED
import com.jetbrains.python.util.runWithModalBlockingOrInBackground
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.io.path.Path


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

  fun withImportedPackages(importedPackages: MutableMap<String, PythonPackage>, settings: PyPackageRequirementsSettings): PyRequirementsAnalysisResult {
    val newCurrentFile = currentFileOutput + importedPackages.values.map {
      if (settings.specifyVersion) "${it.presentableName}${settings.versionSpecifier.separator}${it.version}" else it.presentableName
    }
    return PyRequirementsAnalysisResult(newCurrentFile, baseFilesOutput, unhandledLines, unchangedInBaseFiles)
  }
}

private class PyCollectImportsTask(
  private val module: Module,
  private val psiManager: PsiManager,
  @NlsContexts.DialogTitle title: String,
) : Task.WithResult<Set<String>, Exception>(module.project, title, true) {

  override fun compute(indicator: ProgressIndicator): Set<String> {
    val imported = mutableSetOf<String>()
    ReadAction.run<Throwable> {
      module.rootManager.fileIndex.iterateContent { virtualFile ->
        indicator.checkCanceled()
        val fileName = FileTypeRegistry.getInstance().getFileTypeByFileName(virtualFile.name)

        if (PythonFileType.INSTANCE == fileName || fileName.defaultExtension == "ipynb") {
          val findFile = psiManager.findFile(virtualFile)
          val pyFile = findFile?.viewProvider?.allFiles?.firstOrNull { it is PyFile } as? PyFile ?: return@iterateContent true
          addImports(pyFile, imported)
        }
        return@iterateContent true
      }
    }
    return imported
  }
}

internal fun syncWithImports(module: Module) {
  val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Sync Python requirements")
  val sdk = PythonSdkUtil.findPythonSdk(module)
  if (sdk == null) {
    val configureSdkAction = NotificationAction.createSimpleExpiring(PySdkBundle.message("python.configure.interpreter.action")) {
      PySdkPopupFactory.createAndShow(module)
    }
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.ERROR,
      text = PyBundle.message("python.requirements.error.no.interpreter"),
      project = module.project,
      action = configureSdkAction,
      displayId = NO_INTERPRETER_CONFIGURED
    )
    return
  }
  val settings = PyPackageRequirementsSettings.getInstance(module)

  if (!ApplicationManager.getApplication().isUnitTestMode) {
    val proceed = showSyncSettingsDialog(module.project, settings, sdk)
    if (!proceed) return
  }

  val requirementsFile = PyPackageUtil.findRequirementsTxt(module) ?: runWriteAction {
    PythonRequirementTxtSdkUtils.createRequirementsTxtPath(module, sdk)
  }

  if (requirementsFile == null) {
    val text = PyBundle.message("python.requirements.error.create.requirements.file")
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.WARNING,
      text = text,
      project = module.project,
      displayId = CREATE_REQUIREMENTS_FILE_FAILED
    )
    return
  }

  val matchResult = prepareRequirementsText(module, sdk, settings)

  WriteCommandAction.runWriteCommandAction(module.project, PyBundle.message("python.requirements.action.name"), null, {
    val documentManager = FileDocumentManager.getInstance()
    documentManager.getDocument(requirementsFile)!!.setText(matchResult.currentFileOutput.joinToString("\n"))
    matchResult.baseFilesOutput.forEach { (file, content) ->
      documentManager.getDocument(file)!!.setText(content.joinToString("\n"))
    }
  })
  val psiManager = PsiManager.getInstance(module.project)
  psiManager.findFile(requirementsFile)?.navigate(true)

  if (matchResult.unhandledLines.isNotEmpty()) {
    val text = PyBundle.message("python.requirements.warning.unhandled.lines", matchResult.unhandledLines.joinToString(", "))
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.WARNING,
      text = text,
      project = module.project,
      displayId = ANALYZE_ENTRIES_IN_REQUIREMENTS_FILE_FAILED
    )
  }
  if (matchResult.unchangedInBaseFiles.isNotEmpty()) {
    val text = PyBundle.message("python.requirements.info.file.ref.dropped", matchResult.unchangedInBaseFiles.joinToString(", "))
    showNotification(
      notificationGroup = notificationGroup,
      type = NotificationType.INFORMATION,
      text = text,
      project = module.project,
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
  val notification = notificationGroup.createNotification(PyBundle.message("python.requirements.balloon"), text, type).setDisplayId(displayId)
  if (action != null) notification.addAction(action)
  notification.notify(project)
}


private fun prepareRequirementsText(module: Module, sdk: Sdk, settings: PyPackageRequirementsSettings): PyRequirementsAnalysisResult {
  val psiManager = PsiManager.getInstance(module.project)

  val dialogTitle = PyBundle.message("python.requirements.analyzing.imports.title")
  val task = PyCollectImportsTask(module, psiManager, dialogTitle)
  task.queue()

  val installedPackages = runWithModalBlockingOrInBackground(module.project, PyBundle.message("python.packaging.list.packages")) {
    PythonPackageManager.forSdk(module.project, sdk).listInstalledPackages()
  }

  val importedPackages = task.result.asSequence()
    .flatMap { topLevelPackage ->
      val alias = PyPsiPackageUtil.moduleToPackageName(topLevelPackage, default = "")
      sequence {
        yield(topLevelPackage)
        if (alias.isNotEmpty()) yield(alias)
      }.mapNotNull { name -> installedPackages.find { StringUtil.equalsIgnoreCase(it.name, name) } }
    }
    .map { it.name.lowercase(Locale.getDefault()) to it }
    .toMap(mutableMapOf())

  val analysisResult = when (val requirementsFile = PyPackageUtil.findRequirementsTxt(module)) {
    null -> PyRequirementsAnalysisResult.empty()
    else -> {
      val visitor = PyRequirementsFileVisitor(importedPackages, settings)
      visitor.visitRequirementsFile(psiManager.findFile(requirementsFile)!!)
    }
  }

  return analysisResult.withImportedPackages(importedPackages, settings)
}

private fun showSyncSettingsDialog(project: Project, settings: PyPackageRequirementsSettings, sdk: Sdk): Boolean {
  val ref = Ref.create(false)
  val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
  val panel = panel {
    val sdkAdditionalData = sdk.sdkAdditionalData as? PythonSdkAdditionalData
    if (sdkAdditionalData != null) {
      row(PyBundle.message("form.integrated.tools.package.requirements.file")) {
        textFieldWithBrowseButton(fileChooserDescriptor = descriptor)
          .bindText({
                      sdkAdditionalData.requiredTxtPath?.toString() ?: ""
                    }, { stringPath ->
                      sdkAdditionalData.requiredTxtPath = runCatching {
                        stringPath.ifBlank { null }?.let { Path(it) }
                      }.getOrNull()
                    })
          .align(AlignX.FILL)
          .focused()
      }
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


  ApplicationManager.getApplication().invokeAndWait { ref.set(dialog.showAndGet()) }

  return ref.get()
}

private fun addImports(file: PyFile, imported: MutableSet<String>) {
  (file.importTargets.asSequence().mapNotNull { it.importedQName?.firstComponent } +
   file.fromImports.asSequence().mapNotNull { it.importSourceQName?.firstComponent })
    .forEach { imported.add(it) }
}
