// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.PythonSdkUtil
import java.nio.file.Paths


/**
 * Holder class for generated requirements.
 * @param currentFileOutput content of existing requirements file, if any, with added missing entries
 * @param baseFilesOutput content of base files, referenced from the original file
 * @param unhandledLines lines that we failed to analyze
 * @param unchangedInBaseFiles packages with different versions to notify the user about if modification of base files is not allowed
 */
data class PyRequirementsAnalysisResult(val currentFileOutput: List<String>,
                                        val baseFilesOutput: Map<VirtualFile, List<String>>,
                                        val unhandledLines: List<String>,
                                        val unchangedInBaseFiles: List<String>) {
  companion object {
    fun empty() = PyRequirementsAnalysisResult(emptyList(), emptyMap(), emptyList(), emptyList())
  }

  fun withImportedPackages(importedPackages: Map<String, PyPackage>, settings: PyPackageRequirementsSettings): PyRequirementsAnalysisResult {
    val newCurrentFile = currentFileOutput + importedPackages.values.map {
      if (settings.specifyVersion) "${it.name}${settings.versionSpecifier.separator}${it.version}" else it.name
    }
    return PyRequirementsAnalysisResult(newCurrentFile, baseFilesOutput, unhandledLines, unchangedInBaseFiles)
  }
}

private class PyCollectImportsTask(
  private val module: Module,
  private val psiManager: PsiManager,
  @NlsContexts.DialogTitle title: String
) : Task.WithResult<Set<String>, Exception>(module.project, title, true) {

  override fun compute(indicator: ProgressIndicator): Set<String> {
    val imported = mutableSetOf<String>()
    ReadAction.run<Throwable> {
      module.rootManager.fileIndex.iterateContent {
        indicator.checkCanceled()
        if (PythonFileType.INSTANCE == FileTypeRegistry.getInstance().getFileTypeByFileName(it.name)) {
          addImports(psiManager.findFile(it) as PyFile, imported)
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
    showNotification(notificationGroup,
                     NotificationType.ERROR,
                     PyBundle.message("python.requirements.error.no.interpreter"),
                     module.project,
                     configureSdkAction)
    return
  }
  val settings = PyPackageRequirementsSettings.getInstance(module)

  if (!ApplicationManager.getApplication().isUnitTestMode) {
    val proceed = showSyncSettingsDialog(module.project, settings)
    if (!proceed) return
  }

  var requirementsFile = PyPackageUtil.findRequirementsTxt(module)
  val matchResult = prepareRequirementsText(module, sdk, settings)

  val psiManager = PsiManager.getInstance(module.project)
  WriteCommandAction.runWriteCommandAction(module.project, PyBundle.message("python.requirements.action.name"), null, {
    if (requirementsFile == null) {
      val path = Paths.get(settings.requirementsPath)
      val location = when {
        path.parent != null -> LocalFileSystem.getInstance().findFileByPath(path.parent.toString())!!
        else -> module.rootManager.contentRoots.first()
      }
      val root = psiManager.findDirectory(location)!!

      var psiFile = root.findFile(path.fileName.toString())
      if (psiFile == null) psiFile = root.createFile(path.fileName.toString())

      requirementsFile = psiFile.virtualFile
    }
    val documentManager = FileDocumentManager.getInstance()
    documentManager.getDocument(requirementsFile!!)!!.setText(matchResult.currentFileOutput.joinToString("\n"))
    matchResult.baseFilesOutput.forEach { (file, content) ->
      documentManager.getDocument(file)!!.setText(content.joinToString("\n"))
    }
  })
  psiManager.findFile(requirementsFile!!)?.navigate(true)
  if (matchResult.unhandledLines.isNotEmpty()) {
    val text = PyBundle.message("python.requirements.warning.unhandled.lines", matchResult.unhandledLines.joinToString(", "))
    showNotification(notificationGroup, NotificationType.WARNING, text, module.project)
  }
  if (matchResult.unchangedInBaseFiles.isNotEmpty()) {
    val text = PyBundle.message("python.requirements.info.file.ref.dropped", matchResult.unchangedInBaseFiles.joinToString(", "))
    showNotification(notificationGroup, NotificationType.INFORMATION, text, module.project)
  }
}

private fun showNotification(notificationGroup: NotificationGroup,
                             type: NotificationType,
                             @NlsContexts.NotificationContent text: String,
                             project: Project,
                             action: NotificationAction? = null) {
  val notification = notificationGroup.createNotification(PyBundle.message("python.requirements.balloon"), text, type)
  if (action != null) notification.addAction(action)
  notification.notify(project)
}


private fun prepareRequirementsText(module: Module, sdk: Sdk, settings: PyPackageRequirementsSettings): PyRequirementsAnalysisResult {
  val psiManager = PsiManager.getInstance(module.project)

  val dialogTitle = PyBundle.message("python.requirements.analyzing.imports.title")
  val task = PyCollectImportsTask(module, psiManager, dialogTitle)
  task.queue()

  val installedPackages = PyPackageManager.getInstance(sdk).refreshAndGetPackages(false)
  val importedPackages = task.result.asSequence()
    .flatMap { topLevelPackage ->
      val alias = PyPsiPackageUtil.moduleToPackageName(topLevelPackage, default = "")
      sequence {  
        yield(topLevelPackage)
        if (alias.isNotEmpty()) yield(alias)
      }.mapNotNull { name -> installedPackages.find { StringUtil.equalsIgnoreCase(it.name, name) } }
    }
    .map { it.name.toLowerCase() to it }
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

private fun showSyncSettingsDialog(project: Project, settings: PyPackageRequirementsSettings): Boolean {
  val ref = Ref.create(false)
  val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
  val panel = panel {
    row(PyBundle.message("form.integrated.tools.package.requirements.file")) {
      textFieldWithBrowseButton(fileChooserDescriptor = descriptor)
        .bindText(settings::getRequirementsPath, settings::setRequirementsPath)
        .align(AlignX.FILL)
        .focused()
    }
    row(PyBundle.message("python.requirements.version.label")) {
      comboBox(PyRequirementsVersionSpecifierType.values().asList())
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
