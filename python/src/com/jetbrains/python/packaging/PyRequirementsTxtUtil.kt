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
import com.intellij.psi.PsiFile
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
  var requirementsFile = PyPackageUtil.findRequirementsTxt(module)
  val settings = PyPackageRequirementsSettings.getInstance(module)

  if (requirementsFile == null) {
    val proceed = showSpecifyRequirementsFileDialog(module.project, settings)
    if (!proceed) return
  }

  requirementsFile = PyPackageUtil.findRequirementsTxt(module) // user might have specified an existing file
  val notificationGroup = NotificationGroup.balloonGroup(PyBundle.message("python.requirements.balloon"))
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
    FileDocumentManager.getInstance().getDocument(requirementsFile!!)!!.setText(matchResult.lines.joinToString("\n"))
  }
  psiManager.findFile(requirementsFile!!)?.navigate(true)
  if (matchResult.unhandled.isNotEmpty()) {
    val text = PyBundle.message("python.requirements.warning.unhandled.lines", matchResult.unhandled.joinToString(", "))
    notificationGroup
      .createNotification(PyBundle.message("python.requirements.balloon"), text, NotificationType.WARNING)
      .notify(module.project)
  }
  if (matchResult.fileReferenceDropped) {
    val text = PyBundle.message("python.requirements.info.file.ref.dropped")
    notificationGroup
      .createNotification(PyBundle.message("python.requirements.balloon"), text, NotificationType.INFORMATION)
      .notify(module.project)
  }
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
            println(it.path)
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
    .map { PyPsiPackageUtil.PACKAGES_TOPLEVEL[it]?.first() ?: it }
    .mapNotNull { name -> installedPackages.find { StringUtil.equalsIgnoreCase(it.name, name) } }
    .map { it.name.toLowerCase() to it }
    .toMap(mutableMapOf())

  // the user might have specified existing requirements file, so we have to check again
  val requirementsFile = PyPackageUtil.findRequirementsTxt(module)

  val analysisResult = when {
    requirementsFile != null -> matchWithExistingRequirements(psiManager.findFile(requirementsFile)!!, importedPackages, settings)
    else -> PyRequirementsAnalysisResult.empty()
  }

  importedPackages.values.asSequence()
    .map { if (settings.specifyVersion) "${it.name}${settings.versionSpecifier.separator}${it.version}" else it.name }
    .forEach { analysisResult.lines.add(it) }

  return analysisResult
}

private fun matchWithExistingRequirements(requirementsFile: PsiFile,
                                          importedPackages: MutableMap<String, PyPackage>,
                                          settings: PyPackageRequirementsSettings): PyRequirementsAnalysisResult {
  val outputLines = mutableListOf<String>()
  val errors = mutableListOf<String>()
  var fileReferenceDropped = false

  val resultList = splitByRequirementsEntries(requirementsFile.text)

  for (lines in resultList) {
    if (lines.size == 1) {
      val firstLine = lines.first()
      if (firstLine.isBlank()) continue
      if (firstLine.startsWith("#")) {
        outputLines.add(firstLine)
        continue
      }
    }

    val line = lines.asSequence()
      .map { it.trim() }
      .map { it.removeSuffix("\\") }
      .joinToString(" ")

    if (line.startsWith("--") || line.startsWith("-e .")) {
      outputLines.addAll(lines)
      continue
    }

    val parsed = PyRequirementParser.fromText(line, requirementsFile.virtualFile, mutableSetOf())
    if (line.startsWith("-r ")) {
      if (parsed.isEmpty()) continue

      val (inImports, notInImports) = parsed.partition { it.name.toLowerCase() in importedPackages }
      if (notInImports.isNotEmpty()) {
        // drop -r, add requirements to current file
        fileReferenceDropped = true
        inImports.asSequence()
          .onEach { importedPackages.remove(it.name.toLowerCase()) }
          .map { convertToRequirementsEntry(it, settings) }
          .forEach { outputLines.add(it) }
      }
      else {
        val (_, incompatible) = inImports.partition {
          compatibleVersion(it, importedPackages[it.name.toLowerCase()]!!.version, settings.specifyVersion)
        }
        if (incompatible.isNotEmpty()) {
          // drop -r, add requirements to current file
          fileReferenceDropped = true
          inImports.asSequence()
            .map { it to importedPackages.remove(it.name.toLowerCase()) }
            .map { convertToRequirementsEntry(it.first, settings, it.second?.version)  }
            .forEach { outputLines.add(it) }
        }
        else {
          inImports.forEach { importedPackages.remove(it.name.toLowerCase()) }
          outputLines.addAll(lines)
        }
      }
    }
    else if (parsed.isEmpty()) {
      errors.add(line)
    }
    else {
      val requirement = parsed.first()
      val name = requirement.name.toLowerCase()
      // processing only requirements that are used in the project, discarding others
      if (name in importedPackages) {
        val pkg = importedPackages.remove(name)!!
        if (compatibleVersion(requirement, pkg.version, settings.specifyVersion)) {
          outputLines.addAll(lines)
        }
        else {
          outputLines.add(convertToRequirementsEntry(requirement, settings, pkg.version))
        }
      }
    }
  }
  return PyRequirementsAnalysisResult(outputLines, errors, fileReferenceDropped)
}

private fun splitByRequirementsEntries(requirementsText: String): MutableList<List<String>> {
  var splitList = mutableListOf<String>()
  val resultList = mutableListOf<List<String>>()
  requirementsText.lines().forEach {
    splitList.add(it)
    if (!it.endsWith("\\")) {
      resultList.add(splitList)
      splitList = mutableListOf()
    }
  }
  if (splitList.isNotEmpty()) error(PyBundle.message("python.requirements.error.ends.with.slash"))
  return resultList
}

private fun compatibleVersion(requirement: PyRequirement, version: String, specifyVersion: Boolean): Boolean = when {
  specifyVersion -> requirement.versionSpecs.isNotEmpty() && requirement.versionSpecs.all { it.matches(version) }
  else -> requirement.versionSpecs.isEmpty()
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
      label(PyBundle.message("form.integrated.tools.packaging.version.type"))
      comboBox(DefaultComboBoxModel(PyRequirementsVersionSpecifierType.values()),
               { settings.versionSpecifier },
               { settings.versionSpecifier = it })
    }
  }
  val dialog = dialog(title = PyBundle.message("python.requirements.requirements.file.dialog.header"),
                      panel = panel,
                      resizable = true,
                      project = project)


  ApplicationManager.getApplication().invokeAndWait { ref.set(dialog.showAndGet()) }

  return ref.get()
}


private fun convertToRequirementsEntry(requirement: PyRequirement, settings: PyPackageRequirementsSettings, version: String? = null): String {
  val packageName = when {
    requirement.isEditable -> "${requirement.installOptions[0]} ${requirement.installOptions[1]}${requirement.extras}"
    settings.specifyVersion -> when {
      version != null -> requirement.name + requirement.extras + settings.versionSpecifier.separator + version
      else -> requirement.presentableText
    }
    else -> requirement.name + requirement.extras
  }

  val optionsToDrop = if (requirement.isEditable) 2 else 1

  if (requirement.installOptions.size == optionsToDrop) return packageName
  val offset = " ".repeat(packageName.length + 1)
  val installOptions = requirement.installOptions.drop(optionsToDrop).joinToString(separator = "\\\n$offset")
  return "$packageName $installOptions"
}


private fun addImports(file: PyFile, imported: MutableSet<String>) {
  (file.importTargets.asSequence().mapNotNull { it.importedQName?.firstComponent } +
  file.fromImports.asSequence().mapNotNull { it.importSourceQName?.firstComponent })
    .forEach { imported.add(it) }
}


data class PyRequirementsAnalysisResult(val lines: MutableList<String>,
                                        val unhandled: List<String>,
                                        var fileReferenceDropped: Boolean) {
  companion object {
    fun empty() = PyRequirementsAnalysisResult(mutableListOf(), emptyList(), false)
  }
}
