// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.requirements.parser

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.childrenOfType
import com.intellij.python.requirements.PyRequirementEnvMarkerAndSet
import com.intellij.python.requirements.PyRequirementEnvMarkerImpl
import com.intellij.python.requirements.PyRequirementEnvMarkerOrSet
import com.intellij.python.requirements.PyRequirementEnvMarkerRelation
import com.intellij.python.requirements.PyRequirementImpl
import com.intellij.python.requirements.RequirementsLanguage
import com.intellij.python.requirements.parser.psi.EditableOption
import com.intellij.python.requirements.parser.psi.Extras
import com.intellij.python.requirements.parser.psi.LongOption
import com.intellij.python.requirements.parser.psi.MarkerOr
import com.intellij.python.requirements.parser.psi.NameReq
import com.intellij.python.requirements.parser.psi.Option
import com.intellij.python.requirements.parser.psi.QuotedMarker
import com.intellij.python.requirements.parser.psi.UrlReq
import com.intellij.python.requirements.pyRequirementVersionSpec
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarker
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import org.jetbrains.annotations.ApiStatus


/**
 * @see [`pip install` documentation](https://pip.pypa.io/en/stable/reference/pip_install/)
 * @see [PEP-508](https://www.python.org/dev/peps/pep-0508/)
 * @see [PEP-440](https://www.python.org/dev/peps/pep-0440/)
 * @see PyRequirement
 */
object PyRequirementParser {

  @JvmStatic
  fun fromText(text: String): List<PyRequirement> = fromText(text,null)

  @JvmStatic
  fun fromText(text: String, project: Project): List<PyRequirement> = fromText(text, project, null)

  @JvmStatic
  fun fromLine(text: String): PyRequirement? = fromLine(text, ProjectManager.getInstance().defaultProject)

  @JvmStatic
  fun fromLine(text: String, project: Project): PyRequirement? = fromText(text, project, null).firstOrNull()

  @JvmStatic
  fun fromFile(file: VirtualFile): List<PyRequirement> = fromFile(file, ProjectManager.getInstance().defaultProject)

  @JvmStatic
  fun fromFile(file: VirtualFile, project: Project): List<PyRequirement> = fromFile(file, project, HashSet())

  private fun fromFile(file: VirtualFile, project: Project, visitedFiles: MutableSet<VirtualFile>): List<PyRequirement> = runReadActionBlocking {
    val document = FileDocumentManager.getInstance().getDocument(file)
    if (document == null) emptyList() else fromText(document.text, project, file, visitedFiles)
  }

  @JvmStatic
  fun fromText(
    text: String,
    containingFile: VirtualFile? = null,
    visitedFiles: MutableSet<VirtualFile> = HashSet(),
  ): List<PyRequirement> = fromText(text, ProjectManager.getInstance().defaultProject, containingFile, visitedFiles)

  internal fun fromText(
    text: String,
    project: Project,
    containingFile: VirtualFile? = null,
    visitedFiles: MutableSet<VirtualFile> = HashSet(),
  ): List<PyRequirement> {
    if (containingFile != null) {
      if (visitedFiles.contains(containingFile)) return emptyList()
      visitedFiles.add(containingFile)
    }

    // Parsing builds a throwaway PSI file, so read access is required. Acquire it here (read actions are
    // re-entrant) so background callers don't each have to wrap the call in their own read action.
    return runReadActionBlocking {
      val file = PsiFileFactory.getInstance(project).createFileFromText(
        containingFile?.name ?: "", RequirementsLanguage, text
      )
      val requirements = ArrayList<PyRequirement>()

      // Add the requirements from the current file to the list
      requirements.addAll(fromPsiElements(file.children).map { it.second })

      // If an actual requirements file path was passed, process any linked (-r requirements.txt) requirements
      containingFile?.parent?.let { parent ->
        file.children.filterIsInstance<Option>()
          .filter { it.shortOption?.shortOptionName?.text == "-r" || it.longOption?.longOptionName?.text == "--requirement" }
          .mapNotNull { it.shortOption?.optionValue?.text ?: it.longOption?.optionValue?.text }
          .mapNotNull { parent.findFileByRelativePath(it) }
          .forEach { requirements.addAll(fromFile(it, project, visitedFiles)) }
      }

      requirements
    }
  }

  internal fun fromPsiElements(elements: Array<PsiElement>): List<Pair<PsiElement, PyRequirement>> {
    val requirements = ArrayList<Pair<PsiElement, PyRequirement>>()
    elements.forEach { element ->
      val globalOptions = ArrayList<String>()
      when (element) {
        is Option -> {
          // Don't add -r / --requirement to install options as it's handled specially elsewhere
          if (element.shortOption?.shortOptionName?.text == "-r") return@forEach
          if (element.longOption?.longOptionName?.text == "--requirement") return@forEach
          globalOptions.add(element.text)
        }
        is NameReq -> {
          val versionSpecs = element.versionspec?.versionOneList?.mapNotNull {
            val relation = PyRequirementRelation.entries.firstOrNull {
              rel -> rel.presentableText == it.versionCmp.text
            } ?: PyRequirementRelation.EQ
            pyRequirementVersionSpec(relation, it.version?.text ?: return@forEach)
          } ?: emptyList()
          val joinedExtras = getJoinedExtras(element.extras)
          val req = PyRequirementImpl(
            element.packageName.text,
            versionSpecs,
            getInstallOptions(element, element.packageName.text, joinedExtras, element.editableOption, globalOptions),
            joinedExtras,
            getEnvironmentMarker(element.quotedMarker)
          )
          requirements.add(Pair(element, req))
        }
        is UrlReq -> {
          val joinedExtras = getJoinedExtras(element.extras)
          val req = PyRequirementImpl(
            element.packageName.text,
            emptyList(),
            getInstallOptions(element, element.packageName.text, joinedExtras, element.editableOption, globalOptions),
            joinedExtras,
            getEnvironmentMarker(element.quotedMarker),
            (element.uri ?: element.gitUri ?: element.bzrLaunchpadUri)!!.text
          )
          requirements.add(Pair(element, req))
        }
      }
    }
    return requirements
  }

  private fun getJoinedExtras(extras: Extras?) : String {
    return extras?.extrasList?.packageNameList?.joinToString(",") { it.text } ?: ""
  }

  private fun getInstallOptions(
    element: PsiElement,
    name: String,
    joinedExtras: String,
    editableOption: EditableOption?,
    globalOptions: List<String>,
  ): List<String> {
    val installOptions = ArrayList(globalOptions)
    editableOption?.let { installOptions.add(it.text) }
    installOptions.add(if (joinedExtras.isEmpty()) name else "${name}[${joinedExtras}]")
    installOptions.addAll(element.childrenOfType<LongOption>().map { it.text })
    return installOptions
  }

  private fun getEnvironmentMarker(quotedMarker: QuotedMarker?) : PyRequirementEnvMarker? {
    // A half-typed `pkg; ` marker has no `markerOr` yet; skip instead of dereferencing (!!) it.
    return quotedMarker?.markerOr?.let { transformEnvironmentMarker(it) }
  }

  private fun transformEnvironmentMarker(markerOr: MarkerOr) : PyRequirementEnvMarker? {
    val orMarkers = markerOr.markerAndList.mapNotNull { markerAnd ->
      val andMarkers = markerAnd.markerExprList.mapNotNull { expr ->
        expr.markerOr?.let { return@mapNotNull transformEnvironmentMarker(it) }
        // Every part below can be absent while the marker is being typed; skip such incomplete
        // expressions instead of dereferencing (!!) them, which would abort the highlighting pass.
        // Unknown marker names are also ordinary user input and are simply skipped here.
        val markerName = expr.markerName?.text?.uppercase() ?: return@mapNotNull null
        val markerType = PyRequirementEnvMarkerType.entries.firstOrNull { it.name == markerName } ?: return@mapNotNull null
        val pythonStr = expr.pythonStr?.text ?: return@mapNotNull null
        val markerValue = if (pythonStr.length >= 2) pythonStr.substring(1, pythonStr.length - 1) else return@mapNotNull null
        val relation = expr.markerOp?.text?.lowercase()?.let { PyRequirementEnvMarkerRelation.get(it) } ?: return@mapNotNull null
        PyRequirementEnvMarkerImpl(markerType, relation, markerValue.split(",").map { it.trim() })
      }

      when (andMarkers.size) {
        0 -> null
        1 -> andMarkers[0]
        else -> PyRequirementEnvMarkerAndSet(andMarkers)
      }
    }

    return when (orMarkers.size) {
      0 -> null
      1 -> orMarkers[0]
      else -> PyRequirementEnvMarkerOrSet(orMarkers)
    }
  }

  @ApiStatus.Internal
  fun parseVersionSpecs(versionSpecs: String?) : List<PyRequirementVersionSpec> {
    var trimmedVersionSpecs = versionSpecs?.trim() ?: return emptyList()
    if (versionSpecs.startsWith("(") && versionSpecs.endsWith(")")) {
      trimmedVersionSpecs = versionSpecs.substring(1, versionSpecs.length - 1)
    }

    return StringUtil.tokenize(trimmedVersionSpecs, ",")
      .mapNotNull { parseVersionSpec(it) }
  }

  private fun parseVersionSpec(versionSpec: String): PyRequirementVersionSpec? {
    var relation: PyRequirementRelation? = null

    if (versionSpec.startsWith("===")) {
      relation = PyRequirementRelation.STR_EQ
    }
    else if (versionSpec.startsWith("==")) {
      relation = PyRequirementRelation.EQ
    }
    else if (versionSpec.startsWith("<=")) {
      relation = PyRequirementRelation.LTE
    }
    else if (versionSpec.startsWith(">=")) {
      relation = PyRequirementRelation.GTE
    }
    else if (versionSpec.startsWith("<")) {
      relation = PyRequirementRelation.LT
    }
    else if (versionSpec.startsWith(">")) {
      relation = PyRequirementRelation.GT
    }
    else if (versionSpec.startsWith("~=")) {
      relation = PyRequirementRelation.COMPATIBLE
    }
    else if (versionSpec.startsWith("!=")) {
      relation = PyRequirementRelation.NE
    }

    if (relation != null) {
      val version = versionSpec.substring(relation.presentableText.length).trim()
      return pyRequirementVersionSpec(relation, version)
    }

    return null
  }
}
