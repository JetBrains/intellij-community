// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkUtil
import java.nio.file.Paths
import javax.swing.DefaultComboBoxModel


fun syncWithImports(module: Module) {
  val notificationGroup = NotificationGroup.balloonGroup(PyBundle.message("python.requirements.balloon"))
  val sdk = PythonSdkUtil.findPythonSdk(module)
  if (sdk == null) {
    showNotification(notificationGroup, NotificationType.ERROR, PyBundle.message("python.requirements.error.no.interpreter"), module.project)
    return
  }
  val settings = PyPackageRequirementsSettings.getInstance(module)

  if (!ApplicationManager.getApplication().isUnitTestMode) {
    val proceed = showSpecifyRequirementsFileDialog(module.project, settings)
    if (!proceed) return
  }

  var requirementsFile = PyPackageUtil.findRequirementsTxt(module)
  val matchResult = try {
    prepareRequirementsText(module, settings)
  } catch (e: IllegalStateException) {
    notificationGroup
      .createNotification(PyBundle.message("python.requirements.balloon"), e.message!!, NotificationType.ERROR)
      .notify(module.project)
    return
  }
  if (matchResult == null) return

  val psiManager = PsiManager.getInstance(module.project)
  WriteCommandAction.runWriteCommandAction(module.project) {
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
  }
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

private fun showNotification(notificationGroup: NotificationGroup, type: NotificationType, text: String, project: Project) {
  notificationGroup
    .createNotification(PyBundle.message("python.requirements.balloon"), text, type)
    .notify(project)
}

fun prepareRequirementsText(module: Module, settings: PyPackageRequirementsSettings): PyRequirementsAnalysisResult? {
  val sdk = PythonSdkUtil.findPythonSdk(module) ?: return PyRequirementsAnalysisResult.empty()
  val psiManager = PsiManager.getInstance(module.project)

  val imported = mutableSetOf<String>()
  var canceled = false

  object : Task.Modal(module.project, PyBundle.message("python.requirements.analyzing.imports.title"), true) {
    override fun run(indicator: ProgressIndicator) {
      ReadAction.run<Throwable> {
        module.rootManager.fileIndex.iterateContent {
          indicator.checkCanceled()
          if (!it.isDirectory && it.extension == "py") {
            addImports(psiManager.findFile(it) as PyFile, imported)
          }
          return@iterateContent true
        }
      }
    }
    override fun onCancel() {
      canceled = true
    }
  }.queue()
  if (canceled) return null

  val installedPackages = PyPackageManager.getInstance(sdk).packages ?: error(PyBundle.message("python.requirements.error.no.packages"))
  val importedPackages = imported.asSequence()
    .flatMap { topLevelPackage ->
      val aliases = PyPsiPackageUtil.PACKAGES_TOPLEVEL[topLevelPackage]?.toTypedArray() ?: emptyArray()
      sequenceOf(topLevelPackage, *aliases)
        .mapNotNull { name -> installedPackages.find { StringUtil.equalsIgnoreCase(it.name, name) } }
    }
    .map { it.name.toLowerCase() to it }
    .toMap(mutableMapOf())

  val requirementsFile = PyPackageUtil.findRequirementsTxt(module)

  val analysisResult = when {
    requirementsFile != null -> PyRequirementsFileVisitor(importedPackages, settings).visitRequirementsFile(psiManager.findFile(requirementsFile)!!)
    else -> PyRequirementsAnalysisResult.empty()
  }

  importedPackages.values.asSequence()
    .map { if (settings.specifyVersion) "${it.name}${settings.versionSpecifier.separator}${it.version}" else it.name }
    .forEach { analysisResult.currentFileOutput.add(it) }

  return analysisResult
}

private fun showSpecifyRequirementsFileDialog(project: Project, settings: PyPackageRequirementsSettings): Boolean {
  val ref = Ref.create(false)
  val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
  val panel = panel {
    row {
      label(PyBundle.message("form.integrated.tools.package.requirements.file"))
      textFieldWithBrowseButton({ settings.requirementsPath },
                                { settings.requirementsPath = it },
                                fileChooserDescriptor = descriptor).focused()
    }
    row {
      label(PyBundle.message("python.requirements.version.label"))
      comboBox(DefaultComboBoxModel(PyRequirementsVersionSpecifierType.values()),
                 { settings.versionSpecifier },
                 { settings.versionSpecifier = it }).constraints(growX)
    }
    row {
      checkBox(PyBundle.message("python.requirements.remove.unused"),
               { settings.removeUnused },
               { settings.removeUnused = it })
    }
    row {
      checkBox(PyBundle.message("python.requirements.modify.base.files"),
               { settings.modifyBaseFiles },
               { settings.modifyBaseFiles = it })
    }
    row {
      checkBox(PyBundle.message("python.requirements.keep.matching.specifier"),
               { settings.keepMatchingSpecifier },
               { settings.keepMatchingSpecifier = it })
    }
  }
  val dialog = dialog(title = PyBundle.message("python.requirements.requirements.file.dialog.header"),
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

data class PyRequirementsAnalysisResult(val currentFileOutput: MutableList<String>,
                                        val baseFilesOutput: MutableMap<VirtualFile, MutableList<String>>,
                                        val unhandledLines: MutableList<String>,
                                        val unchangedInBaseFiles: MutableList<String>) {
  companion object {
    fun empty() = PyRequirementsAnalysisResult(mutableListOf(), mutableMapOf(), mutableListOf(), mutableListOf())
  }
}
