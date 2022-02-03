// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dsl

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.idea.maven.dom.MavenDomElement
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.model.MavenDomRepository
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager

val mavenTopLevelElementsOrder = listOf(
  "modelVersion",
  "parent",
  "groupId",
  "artifactId",
  "version",
  "packaging",
  "properties",

  "name",
  "description",
  "url",
  "inceptionYear",
  "licenses",
  "organization",
  "developers",
  "contributors",

  "modules",
  "dependencyManagement",
  "dependencies",
  "build",
  "reporting",

  "issueManagement",
  "ciManagement",
  "mailingLists",
  "scm",
  "prerequisites",
  "repositories",
  "pluginRepositories",
  "distributionManagement",
  "profiles"
)

val elementsBeforeDependencies = elementsBefore("dependencies")
val elementsBeforeRepositories = elementsBefore("repositories")
val elementsBeforeDependencyVersion = setOf("artifactId", "groupId")
val elementsBeforeDependencyScope = elementsBeforeDependencyVersion + setOf("version", "classifier", "type")

fun elementsBefore(s: String): Set<String> =
  mavenTopLevelElementsOrder.takeWhile { it != s }
    .toSet()

class MavenDependencyModificator(private val myProject: Project) : ExternalDependencyModificator {
  private val myProjectsManager: MavenProjectsManager = MavenProjectsManager.getInstance(myProject)
  private val myDocumentManager: PsiDocumentManager = PsiDocumentManager.getInstance(myProject)


  private fun addDependenciesTagIfNotExists(psiFile: XmlFile, model: MavenDomProjectModel) {
    addTagIfNotExists(psiFile, model.dependencies, elementsBeforeDependencies)
  }

  private fun addRepositoriesTagIfNotExists(psiFile: XmlFile, model: MavenDomProjectModel) {
    addTagIfNotExists(psiFile, model.repositories, elementsBeforeRepositories)
  }

  private fun addTagIfNotExists(tagName: String, parentElement: MavenDomElement, siblingsBeforeTag: Set<String>) {
    val parentTag = checkNotNull(parentElement.xmlTag) { "Parent element ${parentElement.xmlElementName} must be an XML tag" }
    val xmlTagSiblings = parentTag.childrenOfType<XmlTag>()
    if (xmlTagSiblings.any { it.name == tagName }) return

    val siblingToInsertAfterOrNull = xmlTagSiblings.lastOrNull { siblingsBeforeTag.contains(it.name) }

    val child = parentTag.createChildTag(tagName, parentTag.namespace, null, false)
    parentTag.addAfter(child, siblingToInsertAfterOrNull)
  }

  private fun addTagIfNotExists(psiFile: XmlFile, element: MavenDomElement, elementsBefore: Set<String>) {
    if (element.exists()) {
      return
    }
    val rootTag = psiFile.rootTag
    if (rootTag == null) {
      element.ensureTagExists()
      return
    }
    val children = rootTag.children
    if (children.isEmpty()) {
      element.ensureTagExists()
      return
    }
    val lastOrNull = children
      .mapNotNull { it as? XmlTag }
      .lastOrNull { elementsBefore.contains(it.name) }

    val child = rootTag.createChildTag(element.xmlElementName, rootTag.namespace, null, false)
    rootTag.addAfter(child, lastOrNull)
  }

  private fun getDependenciesModel(module: Module,
                                   groupId: String, artifactId: String): Pair<MavenDomProjectModel, MavenDomDependency?> {
    val project: MavenProject = myProjectsManager.findProject(module) ?: throw IllegalArgumentException(MavenProjectBundle.message(
      "maven.project.not.found.for", module.name))

    return ReadAction.compute<Pair<MavenDomProjectModel, MavenDomDependency?>, Throwable> {
      val model = MavenDomUtil.getMavenDomProjectModel(myProject, project.file) ?: throw IllegalStateException(
        MavenProjectBundle.message("maven.model.error", module.name))
      val managedDependency = MavenDependencyCompletionUtil.findManagedDependency(model, myProject, groupId, artifactId)
      return@compute Pair(model, managedDependency)
    }
  }

  override fun supports(module: Module): Boolean {
    return myProjectsManager.isMavenizedModule(module)
  }

  override fun addDependency(module: Module, descriptor: UnifiedDependency) {
    requireNotNull(descriptor.coordinates.groupId)
    requireNotNull(descriptor.coordinates.version)
    requireNotNull(descriptor.coordinates.artifactId)
    val mavenId = descriptor.coordinates.toMavenId()
    val (model, managedDependency) = getDependenciesModel(module, mavenId.groupId!!, mavenId.artifactId!!)

    val psiFile = DomUtil.getFile(model)
    WriteCommandAction.writeCommandAction(myProject, psiFile).compute<Unit, Throwable> {
      addDependenciesTagIfNotExists(psiFile, model)
      val dependency = MavenDomUtil.createDomDependency(model, null)
      dependency.groupId.stringValue = mavenId.groupId
      dependency.artifactId.stringValue = mavenId.artifactId
      val scope = toMavenScope(descriptor.scope, managedDependency?.scope?.stringValue)
      scope?.let { dependency.scope.stringValue = it }
      if (managedDependency == null || managedDependency.version.stringValue != mavenId.version) {
        dependency.version.stringValue = mavenId.version
      }
      saveFile(psiFile)
    }

  }

  override fun updateDependency(module: Module, oldDescriptor: UnifiedDependency, newDescriptor: UnifiedDependency) {
    requireNotNull(newDescriptor.coordinates.groupId)
    requireNotNull(newDescriptor.coordinates.version)
    requireNotNull(newDescriptor.coordinates.artifactId)

    val oldMavenId = oldDescriptor.coordinates.toMavenId()
    val newMavenId = newDescriptor.coordinates.toMavenId()
    val (model, managedDependency) = ReadAction.compute<Pair<MavenDomProjectModel, MavenDomDependency?>, Throwable> {
      getDependenciesModel(module, oldMavenId.groupId!!, oldMavenId.artifactId!!)
    }

    val psiFile = DomUtil.getFile(model)
    WriteCommandAction.writeCommandAction(myProject, psiFile).compute<Unit, Throwable> {
      if (managedDependency != null) {
        updateManagedDependency(model, managedDependency, oldMavenId, newMavenId, oldDescriptor.scope, newDescriptor.scope)
      }
      else {
        updatePlainDependency(model, oldMavenId, newMavenId, oldDescriptor.scope, newDescriptor.scope)
      }
      saveFile(psiFile)
    }
  }

  private fun updateManagedDependency(model: MavenDomProjectModel,
                                      managedDependency: MavenDomDependency,
                                      oldMavenId: MavenId,
                                      newMavenId: MavenId,
                                      oldScope: String?,
                                      newScope: String?) {
    val domDependency = model.dependencies.dependencies.find { dep ->
      dep.artifactId.stringValue == oldMavenId.artifactId
      && dep.groupId.stringValue == oldMavenId.groupId
      && (!dep.version.exists() || dep.version.stringValue == oldMavenId.version)
      && (!dep.scope.exists() || dep.scope.stringValue == oldScope)
    } ?: return

    updateValueIfNeeded(model = model, domDependency = domDependency, domValue = domDependency.version,
      managedDomValue = managedDependency.version, newValue = newMavenId.version,
      siblingsBeforeTag = elementsBeforeDependencyVersion)

    updateValueIfNeeded(model = model, domDependency = domDependency, domValue = domDependency.scope,
      managedDomValue = managedDependency.scope, newValue = newScope,
      siblingsBeforeTag = elementsBeforeDependencyScope)
  }

  private fun updateValueIfNeeded(model: MavenDomProjectModel,
                                  domDependency: MavenDomDependency,
                                  domValue: GenericDomValue<String>,
                                  managedDomValue: GenericDomValue<String>,
                                  newValue: String?,
                                  siblingsBeforeTag: Set<String>) {
    when {
      newValue == null -> domValue.xmlTag?.delete()
      managedDomValue.stringValue != newValue -> {
        addTagIfNotExists(tagName = domValue.xmlElementName, parentElement = domDependency, siblingsBeforeTag = siblingsBeforeTag)
        updateVariableOrValue(model, domValue, newValue)
      }
      else -> domValue.xmlTag?.delete()
    }
  }

  private fun updatePlainDependency(model: MavenDomProjectModel,
                                    oldMavenId: MavenId,
                                    newMavenId: MavenId,
                                    oldScope: String?,
                                    newScope: String?) {
    val domDependency = model.dependencies.dependencies.find { dep ->
      dep.artifactId.stringValue == oldMavenId.artifactId
      && dep.groupId.stringValue == oldMavenId.groupId
      && dep.version.stringValue == oldMavenId.version
      && dep.scope.stringValue == oldScope
    } ?: return

    updateValueIfNeeded(model, domDependency, domDependency.artifactId, oldMavenId.artifactId, newMavenId.artifactId)
    updateValueIfNeeded(model, domDependency, domDependency.groupId, oldMavenId.groupId, newMavenId.groupId)
    updateValueIfNeeded(model, domDependency, domDependency.version, oldMavenId.version, newMavenId.version)
    updateValueIfNeeded(model, domDependency, domDependency.scope, oldScope, newScope)

    //if (oldMavenId.artifactId != newMavenId.artifactId) {
    //  updateVariableOrValue(model, domDependency.artifactId, newMavenId.artifactId!!)
    //}
    //if (oldMavenId.groupId != newMavenId.groupId) {
    //  updateVariableOrValue(model, domDependency.groupId, newMavenId.groupId!!)
    //}
    //if (oldMavenId.version != newMavenId.version) {
    //  updateVariableOrValue(model, domDependency.version, newMavenId.version!!)
    //}
  }

  private fun updateValueIfNeeded(model: MavenDomProjectModel,
                                  domDependency: MavenDomDependency,
                                  domValue: GenericDomValue<String>,
                                  oldValue: String?,
                                  newValue: String?) {
    if (oldValue == newValue) return

    if (newValue != null) {
      addTagIfNotExists(domValue.xmlElementName, domDependency, elementsBeforeDependencyScope)
      updateVariableOrValue(model, domValue, newValue)
    }
    else {
      domDependency.scope.xmlTag?.delete()
    }
  }

  override fun removeDependency(module: Module, descriptor: UnifiedDependency) {
    requireNotNull(descriptor.coordinates.groupId)
    requireNotNull(descriptor.coordinates.version)
    val mavenId = descriptor.coordinates.toMavenId()
    val (model, _) = getDependenciesModel(module, mavenId.groupId!!, mavenId.artifactId!!)

    val psiFile = DomUtil.getFile(model)
    WriteCommandAction.writeCommandAction(myProject, psiFile).compute<Unit, Throwable> {
      for (dep in model.dependencies.dependencies) {
        if (dep.artifactId.stringValue == mavenId.artifactId && dep.groupId.stringValue == mavenId.groupId) {
          dep.xmlTag?.delete()
        }
      }
      if (model.dependencies.dependencies.isEmpty()) {
        model.dependencies.xmlTag?.delete()
      }
      saveFile(psiFile)
    }
  }

  override fun addRepository(module: Module, repository: UnifiedDependencyRepository) {
    val project: MavenProject = myProjectsManager.findProject(module) ?: throw IllegalArgumentException(MavenProjectBundle.message(
      "maven.project.not.found.for", module.name))
    val model = ReadAction.compute<MavenDomProjectModel, Throwable> {
      MavenDomUtil.getMavenDomProjectModel(myProject, project.file) ?: throw IllegalStateException(
        MavenProjectBundle.message("maven.model.error", module.name))
    }
    for (repo in model.repositories.repositories) {
      if (repo.url.stringValue?.trimLastSlash() == repository.url?.trimLastSlash()) {
        return
      }
    }

    val psiFile = DomUtil.getFile(model)
    WriteCommandAction.writeCommandAction(myProject, psiFile).compute<Unit, Throwable> {
      addRepositoriesTagIfNotExists(psiFile, model)
      val repoTag = model.repositories.addRepository()
      repository.id?.let { repoTag.id.stringValue = it }
      repository.name?.let { repoTag.name.stringValue = it }
      repository.url.let { repoTag.url.stringValue = it }
      saveFile(psiFile)
    }
  }

  override fun deleteRepository(module: Module, repository: UnifiedDependencyRepository) {
    val project: MavenProject = myProjectsManager.findProject(module) ?: throw IllegalArgumentException(MavenProjectBundle.message(
      "maven.project.not.found.for", module.name))
    val (model, repo) = ReadAction.compute<Pair<MavenDomProjectModel, MavenDomRepository?>, Throwable> {
      val model = MavenDomUtil.getMavenDomProjectModel(myProject, project.file) ?: throw IllegalStateException(
        MavenProjectBundle.message("maven.model.error", module.name))
      for (repo in model.repositories.repositories) {
        if (repo.url.stringValue?.trimLastSlash() == repository.url?.trimLastSlash()) {
          return@compute Pair(model, repo)
        }
      }
      return@compute null
    }
    if (repo == null) return
    val psiFile = DomUtil.getFile(repo)
    WriteCommandAction.writeCommandAction(myProject, psiFile).compute<Unit, Throwable> {
      repo.xmlTag?.delete()
      if (model.repositories.repositories.isEmpty()) {
        model.repositories.xmlTag?.delete()
      }
      saveFile(psiFile)
    }
  }

  private fun saveFile(psiFile: XmlFile) {
    val document = myDocumentManager.getDocument(psiFile) ?: throw IllegalStateException(MavenProjectBundle.message(
      "maven.model.error", psiFile))
    myDocumentManager.doPostponedOperationsAndUnblockDocument(document)
    FileDocumentManager.getInstance().saveDocument(document)
  }

  private fun updateVariableOrValue(model: MavenDomProjectModel,
                                    domValue: GenericDomValue<String>,
                                    newValue: String) {
    val rawText = domValue.rawText?.trim()
    if (rawText != null && rawText.startsWith("${'$'}{") && rawText.endsWith("}")) {
      val propertyName = rawText.substring(2, rawText.length - 1)
      val subTags = model.properties.xmlTag?.subTags ?: emptyArray()
      for (subTag in subTags) {
        if (subTag.name == propertyName) {
          //TODO: recursive property declaration
          subTag.value.text = newValue
          return
        }
      }
    }
    else {
      domValue.stringValue = newValue
    }
  }

  private fun toMavenScope(scope: String?, managedScope: String?): String? {
    if (managedScope == null) {
      return scope
    }
    if (scope == managedScope) return null
    return scope
  }

  override fun declaredDependencies(module: Module): List<DeclaredDependency> {
    val project = MavenProjectsManager.getInstance(module.project).findProject(module) ?: return emptyList()
    return declaredDependencies(project.file) ?: emptyList()
  }

  //for faster testing
  fun declaredDependencies(file: VirtualFile): List<DeclaredDependency>? {
    return ReadAction.compute<List<DeclaredDependency>?, Throwable> {
      val model = MavenDomUtil.getMavenDomProjectModel(myProject, file) ?: return@compute null
      model.dependencies.dependencies.map { mavenDomDependency ->
        DeclaredDependency(
          groupId = mavenDomDependency.groupId.stringValue,
          artifactId = mavenDomDependency.artifactId.stringValue,
          version = retrieveDependencyVersion(myProject, mavenDomDependency),
          configuration = mavenDomDependency.scope.stringValue,
          dataContext = DataContext { if (CommonDataKeys.PSI_ELEMENT.`is`(it)) mavenDomDependency.xmlElement else null }
        )
      }
    }
  }

  private fun retrieveDependencyVersion(project: Project, dependency: MavenDomDependency): String? {
    val directVersion = dependency.version.stringValue
    if (directVersion != null) return directVersion

    val managingDependency = MavenDomProjectProcessorUtils.searchManagingDependency(dependency, project)
    return managingDependency?.version?.stringValue

  }

  override fun declaredRepositories(module: Module): List<UnifiedDependencyRepository> {
    val project = MavenProjectsManager.getInstance(module.project).findProject(module) ?: return emptyList()
    return ReadAction.compute<List<UnifiedDependencyRepository>, Throwable> {
      val model = MavenDomUtil.getMavenDomProjectModel(myProject, project.file)
                  ?: return@compute emptyList()
      model.repositories.repositories.map {
        UnifiedDependencyRepository(it.id.stringValue, it.name.stringValue, it.url.stringValue ?: "")
      }
    }
  }

}

private fun String.trimLastSlash() = trimEnd('/')

private fun UnifiedCoordinates.toMavenId() = MavenId(groupId, artifactId, version)
