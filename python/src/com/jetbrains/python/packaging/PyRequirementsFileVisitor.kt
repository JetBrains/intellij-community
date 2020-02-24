// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.python.PyBundle

class PyRequirementsFileVisitor(private val importedPackages: MutableMap<String, PyPackage>,
                                private val settings: PyPackageRequirementsSettings) {

  private val collectedOutput: MutableMap<VirtualFile, MutableList<String>> = mutableMapOf()
  private val unmatchedLines: MutableList<String> = mutableListOf()
  private val unchangedInBaseFiles: MutableList<String> = mutableListOf()


  fun visitRequirementsFile(requirementsFile: PsiFile): PyRequirementsAnalysisResult {
    doVisitFile(requirementsFile, mutableSetOf(requirementsFile.virtualFile))
    val currentFileOutput = collectedOutput.remove(requirementsFile.virtualFile)!!
    return PyRequirementsAnalysisResult(currentFileOutput, collectedOutput, unmatchedLines, unchangedInBaseFiles)
  }

  private fun doVisitFile(requirementsFile: PsiFile, visitedFiles: MutableSet<VirtualFile>) {
    val outputLines = mutableListOf<String>()

    val entries = splitByRequirementsEntries(requirementsFile.text)
    for (lines in entries) {
      if (lines.size == 1) {
        val firstLine = lines.first()
        if (firstLine.startsWith("#") || firstLine.isBlank()) {
          outputLines.add(firstLine)
          continue
        }
      }

      val line = lines.asSequence()
        .map { it.trim() }
        .map { it.removeSuffix("\\") }
        .joinToString(" ")

      if (isSkipableInstallOption(line)) {
        outputLines.addAll(lines)
        continue
      }

      if (settings.modifyBaseFiles && isFileReference(line)) {
        val filename = line.split(" ")[1]
        val dir = requirementsFile.containingDirectory.virtualFile
        val virtualFile = dir.findFileByRelativePath(filename)
        if (virtualFile != null && virtualFile !in visitedFiles) {
          visitedFiles.add(virtualFile)
          val baseRequirementsFile = requirementsFile.manager.findFile(virtualFile)!!
          doVisitFile(baseRequirementsFile, visitedFiles)
          visitedFiles.remove(virtualFile)
        }
        outputLines.addAll(lines) // always keeping base file reference
        continue
      }

      val parsed = PyRequirementParser.fromText(line, requirementsFile.virtualFile, mutableSetOf())
      if (isFileReference(line)) {
        if (parsed.isEmpty()) {
          if (!settings.removeUnused) outputLines.addAll(lines)
          continue
        }

        // report those requirements that were not changed in base files
        var inBaseFile = parsed.asSequence()
          .filter { it.name.toLowerCase() in importedPackages }
          .map { it to importedPackages[it.name.toLowerCase()] }

        if (settings.keepMatchingSpecifier) {
          inBaseFile = inBaseFile.filterNot { compatibleVersion(it.first, it.second!!.version, settings.specifyVersion) }
        }
        inBaseFile.forEach { unchangedInBaseFiles.add(it.first.name) }

        parsed.forEach { importedPackages.remove(it.name.toLowerCase())  }
        outputLines.addAll(lines)
      }
      else if (parsed.isEmpty()) {
        if (settings.removeUnused) unmatchedLines.add(line)
        else outputLines.addAll(lines)
      }
      else {
        val requirement = parsed.first()
        val name = requirement.name.toLowerCase()
        // processing only requirements that are used in the project, discarding others
        if (name in importedPackages) {
          val pkg = importedPackages.remove(name)!!
          if (settings.keepMatchingSpecifier && compatibleVersion(requirement, pkg.version, settings.specifyVersion)) {
            outputLines.addAll(lines)
          }
          else {
            outputLines.add(convertToRequirementsEntry(requirement, settings, pkg.version))
          }
        }
        else if (!settings.removeUnused) {
          outputLines.addAll(lines)
        }
      }
    }
    collectedOutput[requirementsFile.virtualFile] = outputLines
  }

  private fun splitByRequirementsEntries(requirementsText: String): MutableList<List<String>> {
    var splitList = mutableListOf<String>()
    val resultList = mutableListOf<List<String>>()
    if (requirementsText.isNotBlank()) {
      requirementsText.lines().forEach {
        splitList.add(it)
        if (!it.endsWith("\\")) {
          resultList.add(splitList)
          splitList = mutableListOf()
        }
      }
    }
    if (splitList.isNotEmpty()) error(PyBundle.message("python.requirements.error.ends.with.slash"))
    return resultList
  }

  private fun compatibleVersion(requirement: PyRequirement, version: String, specifyVersion: Boolean): Boolean = when {
    specifyVersion -> requirement.versionSpecs.isNotEmpty() && requirement.versionSpecs.all { it.matches(version) }
    else -> requirement.versionSpecs.isEmpty()
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

  private fun isSkipableInstallOption(line: String): Boolean =
    isEditableSelf(line) || (line.startsWith("--") && !line.startsWith("--editable") && !isFileReference(line))

  private fun isFileReference(line: String): Boolean = line.startsWith("-r ") || line.startsWith("--requirement ")
  private fun isEditableSelf(line: String): Boolean = line.startsWith("--editable .") || line.startsWith("-e .")
}