// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModifier
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xml.DomUtil
import org.jetbrains.concurrency.Promise
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
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import java.util.*

class MavenProjectModelModifier(private val myProject: Project) : JavaProjectModelModifier() {
  private val myProjectsManager = MavenProjectsManager.getInstance(myProject)

  override fun addModuleDependency(from: Module, to: Module, scope: DependencyScope, exported: Boolean): Promise<Void?>? {
    val toProject = myProjectsManager.findProject(to)
    if (toProject == null) return null
    val mavenId = toProject.mavenId
    return addDependency(mutableListOf<Module>(from), mavenId, scope)
  }

  private fun addDependency(
    fromModules: MutableCollection<Module>,
    mavenId: MavenId,
    scope: DependencyScope,
  ): Promise<Void?>? {
    return addDependency(fromModules, mavenId, null, null, null, scope)
  }

  private fun addDependency(
    fromModules: Collection<Module>,
    mavenId: MavenId,
    minVersion: String?,
    maxVersion: String?,
    preferredVersion: String?, scope: DependencyScope,
  ): Promise<Void?>? {
    val models: MutableList<Trinity<MavenDomProjectModel?, MavenId?, String?>> = ArrayList<Trinity<MavenDomProjectModel?, MavenId?, String?>>(
      fromModules.size)
    val files: MutableList<XmlFile?> = ArrayList<XmlFile?>(fromModules.size)
    val projectToUpdate: MutableList<MavenProject> = ArrayList<MavenProject>(fromModules.size)
    val mavenScope: String = getMavenScope(scope)
    for (from in fromModules) {
      if (!myProjectsManager.isMavenizedModule(from)) return null
      val fromProject = myProjectsManager.findProject(from)
      if (fromProject == null) return null

      val model = getMavenDomProjectModel(myProject, fromProject.file)
      if (model == null) return null

      var scopeToSet: String? = null
      var version: String? = null
      if (mavenId.groupId != null && mavenId.artifactId != null) {
        val managedDependency =
          MavenDependencyCompletionUtil.findManagedDependency(model, myProject, mavenId.groupId!!, mavenId.getArtifactId()!!)
        if (managedDependency != null) {
          val managedScope = StringUtil.nullize(managedDependency.getScope().getStringValue(), true)
          scopeToSet = if ((managedScope == null && MavenConstants.SCOPE_COMPILE == mavenScope) ||
                           StringUtil.equals(managedScope, mavenScope)
          )
            null
          else
            mavenScope
        }

        if (managedDependency == null || StringUtil.isEmpty(managedDependency.getVersion().getStringValue())) {
          version = selectVersion(mavenId, minVersion, maxVersion, preferredVersion)
          scopeToSet = mavenScope
        }
      }

      models.add(
        Trinity.create<MavenDomProjectModel?, MavenId?, String?>(model, MavenId(mavenId.getGroupId(), mavenId.getArtifactId(), version),
                                                                 scopeToSet))
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
    return myProjectsManager.forceUpdateProjects(projectToUpdate)
  }

  override fun addExternalLibraryDependency(
    modules: MutableCollection<out Module>,
    descriptor: ExternalLibraryDescriptor,
    scope: DependencyScope,
  ): Promise<Void?>? {
    for (module in modules) {
      if (!myProjectsManager.isMavenizedModule(module)) {
        return null
      }
    }

    val mavenId = MavenId(descriptor.getLibraryGroupId(), descriptor.getLibraryArtifactId(), null)
    return addDependency(modules, mavenId, descriptor.getMinVersion(), descriptor.getMaxVersion(), descriptor.getPreferredVersion(), scope)
  }

  private fun selectVersion(
    mavenId: MavenId,
    minVersion: String?,
    maxVersion: String?,
    preferredVersion: String?,
  ): String {
    val versions = if (mavenId.groupId == null || mavenId.getArtifactId() == null) mutableSetOf<String?>()
    else
      DependencySearchService.getInstance(myProject).getVersions(mavenId.getGroupId()!!, mavenId.getArtifactId()!!)
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
      return (if (mavenId.getVersion() == null) "RELEASE" else mavenId.getVersion())!!
    }
    return Collections.max<String>(suitableVersions, VersionComparatorUtil.COMPARATOR)
  }

  override fun addLibraryDependency(from: Module, library: Library, scope: DependencyScope, exported: Boolean): Promise<Void?>? {
    val name = library.getName()
    if (name != null && name.startsWith(MavenArtifact.MAVEN_LIB_PREFIX)) {
      //it would be better to use RepositoryLibraryType for libraries imported from Maven and fetch mavenId from the library properties instead
      val mavenCoordinates = name.removePrefix(MavenArtifact.MAVEN_LIB_PREFIX)
      return addDependency(mutableListOf<Module>(from), MavenId(mavenCoordinates), scope)
    }
    return null
  }

  override fun changeLanguageLevel(module: Module, level: LanguageLevel): Promise<Void?>? {
    if (!myProjectsManager.isMavenizedModule(module)) return null

    val mavenProject = myProjectsManager.findProject(module)
    if (mavenProject == null) return null

    val model = getMavenDomProjectModel(myProject, mavenProject.file)
    if (model == null) return null

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
    return myProjectsManager.forceUpdateProjects(mutableSetOf<MavenProject>(mavenProject))
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