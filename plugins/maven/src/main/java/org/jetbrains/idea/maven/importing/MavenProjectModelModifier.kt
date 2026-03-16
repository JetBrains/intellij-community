// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModifier
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xml.DomUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.MavenDomUtil.getMavenDomProjectModel
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.importing.MavenProjectModelModifierUtil.getCompilerPlugin
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenActivityKey
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import java.util.Collections

class MavenProjectModelModifier(private val myProject: Project) : JavaProjectModelModifier() {
  private val myProjectsManager = MavenProjectsManager.getInstance(myProject)

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(val cs: CoroutineScope)

  private fun toPromise(block: suspend CoroutineScope.() -> Unit): Promise<Void?> {
    val result = AsyncPromise<Void?>()
    myProject.trackActivityBlocking(MavenActivityKey) {
      myProject.service<CoroutineScopeService>().cs.launchTracked(Dispatchers.IO) {
        block()
        result.setResult(null)
      }
    }
    return result
  }

  override fun addModuleDependency(from: Module, to: Module, scope: DependencyScope, exported: Boolean): Promise<Void?>? {
    val toProject = myProjectsManager.findProject(to)
    if (toProject == null) return null
    val mavenId = toProject.mavenId
    val fromProject = myProjectsManager.findProject(from) ?: return null
    return toPromise {
      addDependency(listOf(fromProject), mavenId, scope)
    }
  }

  private suspend fun addDependency(
    fromMavenProjects: Collection<MavenProject>,
    mavenId: MavenId,
    scope: DependencyScope,
  ) {
    return addDependency(fromMavenProjects, mavenId, null, null, null, scope)
  }

  private suspend fun addDependency(
    fromMavenProjects: Collection<MavenProject>,
    mavenId: MavenId,
    minVersion: String?,
    maxVersion: String?,
    preferredVersion: String?, scope: DependencyScope,
  ) {
    val models: MutableList<Trinity<MavenDomProjectModel?, MavenId?, String?>> = ArrayList<Trinity<MavenDomProjectModel?, MavenId?, String?>>(
      fromMavenProjects.size)
    val files: MutableList<XmlFile?> = ArrayList<XmlFile?>(fromMavenProjects.size)
    val projectToUpdate: MutableList<MavenProject> = ArrayList<MavenProject>(fromMavenProjects.size)
    val mavenScope: String = getMavenScope(scope)
    for (fromProject in fromMavenProjects) {
      val model = readAction { getMavenDomProjectModel(myProject, fromProject.file) }
      if (model == null) return

      var scopeToSet: String? = null
      var version: String? = null
      if (mavenId.groupId != null && mavenId.artifactId != null) {
        val managedDependency = readAction {
          MavenDependencyCompletionUtil.findManagedDependency(model, myProject, mavenId.groupId!!, mavenId.artifactId!!)
        }
        if (managedDependency != null) {
          val managedScope = readAction { StringUtil.nullize(managedDependency.getScope().getStringValue(), true) }
          scopeToSet = if ((managedScope == null && MavenConstants.SCOPE_COMPILE == mavenScope) || StringUtil.equals(managedScope, mavenScope))
            null
          else
            mavenScope
        }

        val managedDependencyVersion = readAction { managedDependency?.getVersion()?.getStringValue() }
        if (managedDependency == null || StringUtil.isEmpty(managedDependencyVersion)) {
          version = selectVersion(mavenId, minVersion, maxVersion, preferredVersion)
          scopeToSet = mavenScope
        }
      }

      models.add(Trinity.create<MavenDomProjectModel?, MavenId?, String?>(model, MavenId(mavenId.groupId, mavenId.artifactId, version), scopeToSet))
      files.add(DomUtil.getFile(model))
      projectToUpdate.add(fromProject)
    }

    WriteCommandAction.writeCommandAction(myProject, *PsiUtilCore.toPsiFileArray(files)).withName(
      MavenDomBundle.message("fix.add.dependency")).run<RuntimeException?>(
      ThrowableRunnable {
        val pdm = PsiDocumentManager.getInstance(myProject)
        for (trinity in models) {
          val model: MavenDomProjectModel = trinity.first!!
          val dependency = MavenDomUtil.createDomDependency(model, null, trinity.second!!)
          val ms = trinity.third
          if (ms != null) {
            dependency.getScope().setStringValue(ms)
          }
          val document = pdm.getDocument(DomUtil.getFile(model))
          if (document != null) {
            pdm.doPostponedOperationsAndUnblockDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
          }
        }
      })
    val filesToUpdate = projectToUpdate.map { it.file }
    filesToUpdate.forEach { it.refresh(false, false) }
    myProjectsManager.updateMavenProjects(MavenSyncSpec.incremental("MavenProjectModelModifier.addDependency", false), filesToUpdate, emptyList())
  }

  override fun addExternalLibraryDependency(
    modules: Collection<Module>,
    descriptor: ExternalLibraryDescriptor,
    scope: DependencyScope,
  ): Promise<Void?>? {
    val mavenProjects = mutableListOf<MavenProject>()
    for (module in modules) {
      if (!myProjectsManager.isMavenizedModule(module)) {
        return null
      }
      val mavenProject = myProjectsManager.findProject(module) ?: return null
      mavenProjects.add(mavenProject)
    }

    val mavenId = MavenId(descriptor.libraryGroupId, descriptor.libraryArtifactId, null)
    return toPromise {
      addDependency(mavenProjects, mavenId, descriptor.minVersion, descriptor.maxVersion, descriptor.preferredVersion, scope)
    }
  }

  private suspend fun selectVersion(
    mavenId: MavenId,
    minVersion: String?,
    maxVersion: String?,
    preferredVersion: String?,
  ): String {
    val versions = if (mavenId.groupId == null || mavenId.artifactId == null) mutableSetOf<String?>()
    else
      DependencySearchService.getInstance(myProject).getVersionsAsync(mavenId.groupId!!, mavenId.artifactId!!)
    if (preferredVersion != null && versions.contains(preferredVersion)) {
      return preferredVersion
    }
    val suitableVersions: MutableList<String?> = ArrayList<String?>()
    for (version in versions) {
      if ((minVersion == null || VersionComparatorUtil.compare(minVersion, version) <= 0)
          && (maxVersion == null || VersionComparatorUtil.compare(version, maxVersion) <= 0)
      ) {
        suitableVersions.add(version)
      }
    }
    if (suitableVersions.isEmpty()) {
      return mavenId.version ?: "RELEASE"
    }
    return Collections.max<String>(suitableVersions, VersionComparatorUtil.COMPARATOR)
  }

  override fun addLibraryDependency(from: Module, library: Library, scope: DependencyScope, exported: Boolean): Promise<Void?>? {
    val name = library.getName()
    if (name != null && name.startsWith(MavenArtifact.MAVEN_LIB_PREFIX)) {
      //it would be better to use RepositoryLibraryType for libraries imported from Maven and fetch mavenId from the library properties instead
      val mavenCoordinates = name.removePrefix(MavenArtifact.MAVEN_LIB_PREFIX)
      val fromProject = myProjectsManager.findProject(from) ?: return null
      return toPromise {
        addDependency(listOf(fromProject), MavenId(mavenCoordinates), scope)
      }
    }
    return null
  }

  override fun changeLanguageLevel(module: Module, level: LanguageLevel): Promise<Void?>? {
    if (!myProjectsManager.isMavenizedModule(module)) return null

    val mavenProject = myProjectsManager.findProject(module)
    if (mavenProject == null) return null

    val model = ReadAction.nonBlocking<MavenDomProjectModel?> { getMavenDomProjectModel(myProject, mavenProject.file) }.executeSynchronously() ?: return null
    return toPromise {
      WriteCommandAction.writeCommandAction(myProject, DomUtil.getFile(model)).withName(
        MavenDomBundle.message("fix.add.dependency")).run<RuntimeException?>(
        ThrowableRunnable {
          val documentManager = PsiDocumentManager.getInstance(myProject)
          val document = documentManager.getDocument(DomUtil.getFile(model))
          if (document != null) {
            documentManager.commitDocument(document)
          }
          val tag: XmlTag = getCompilerPlugin(model).getConfiguration().ensureTagExists()
          val option = JpsJavaSdkType.complianceOption(level.toJavaVersion())
          setChildTagValue(tag, "source", option)
          setChildTagValue(tag, "target", option)
          if (level.isPreview) {
            setChildTagValue(tag, "compilerArgs", "--enable-preview")
          }
          if (document != null) {
            FileDocumentManager.getInstance().saveDocument(document)
          }
        })
      val filesToUpdate = listOf(mavenProject.file)
      filesToUpdate.forEach { it.refresh(false, false) }
      // even if there are no changes in pom.xml, we may need to update IDEA module with the new language level (if it was changed manually)
      myProjectsManager.updateMavenProjects(MavenSyncSpec.full("MavenProjectModelModifier.changeLanguageLevel", false), filesToUpdate, emptyList())
    }
  }

  private fun setChildTagValue(tag: XmlTag, subTagName: String, value: String) {
    val subTag = tag.findFirstSubTag(subTagName)
    if (subTag != null) {
      subTag.getValue().setText(value)
    }
    else {
      tag.addSubTag(tag.createChildTag(subTagName, tag.getNamespace(), value, false), false)
    }
  }

  private fun getMavenScope(scope: DependencyScope): String {
    return when (scope) {
      DependencyScope.RUNTIME -> MavenConstants.SCOPE_RUNTIME
      DependencyScope.COMPILE -> MavenConstants.SCOPE_COMPILE
      DependencyScope.TEST -> MavenConstants.SCOPE_TEST
      DependencyScope.PROVIDED -> MavenConstants.SCOPE_PROVIDED
    }
  }
}

object MavenProjectModelModifierUtil {
  @JvmStatic
  fun getCompilerPlugin(model: MavenDomProjectModel): MavenDomPlugin {
    var plugin: MavenDomPlugin? = findCompilerPlugin(model)
    if (plugin != null) return plugin
    plugin = model.getBuild().getPlugins().addPlugin()
    plugin.getGroupId().setValue("org.apache.maven.plugins")
    plugin.getArtifactId().setValue("maven-compiler-plugin")
    return plugin
  }

  @JvmStatic
  fun findCompilerPlugin(model: MavenDomProjectModel): MavenDomPlugin? {
    val plugins = model.getBuild().getPlugins()
    for (plugin in plugins.getPlugins()) {
      if ("org.apache.maven.plugins" == plugin.getGroupId().getStringValue() &&
          "maven-compiler-plugin" == plugin.getArtifactId().getStringValue()
      ) {
        return plugin
      }
    }
    return null
  }
}