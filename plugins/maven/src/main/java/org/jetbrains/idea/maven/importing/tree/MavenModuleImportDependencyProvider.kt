// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId.Companion.COMPILED
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId.Companion.SOURCES
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter.Companion.createCopyForLocalRepo
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter.Companion.getAttachedJarsLibName
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter.Companion.selectScope
import org.jetbrains.idea.maven.importing.tree.dependency.*
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter.Companion.JAVADOC_TYPE
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.SupportedRequestType

private const val INITIAL_CAPACITY_TEST_DEPENDENCY_LIST: Int = 4

class MavenModuleImportDependencyProvider(private val moduleImportDataByMavenId: Map<MavenId, MavenProjectImportData>,
                                          importingSettings: MavenImportingSettings,
                                          private val myProjectTree: MavenProjectsTree) {
  private val dependencyTypesFromSettings: Set<String> = importingSettings.dependencyTypesAsSet

  fun getDependencies(importData: MavenProjectImportData): MavenModuleImportDataWithDependencies {
    val mavenProject = importData.mavenProject
    val mainDependencies: MutableList<MavenImportDependency<*>> = ArrayList(mavenProject.dependencies.size)
    val testDependencies: MutableList<MavenImportDependency<*>> = ArrayList(INITIAL_CAPACITY_TEST_DEPENDENCY_LIST)

    addMainDependencyToTestModule(importData, testDependencies)
    val hasSeparateTestModule = importData.splittedMainAndTestModules != null
    for (artifact in mavenProject.dependencies) {
      for (dependency in getDependency(artifact, mavenProject)) {
        if (hasSeparateTestModule && dependency.scope == DependencyScope.TEST) {
          testDependencies.add(dependency)
        }
        else {
          mainDependencies.add(dependency)
        }
      }
    }
    return MavenModuleImportDataWithDependencies(importData, mainDependencies, testDependencies)
  }

  private fun getDependency(artifact: MavenArtifact, mavenProject: MavenProject): List<MavenImportDependency<*>> {
    val dependencyType = artifact.type

    if (!dependencyTypesFromSettings.contains(dependencyType)
        && !mavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType)) {
      return emptyList()
    }

    val scope = selectScope(artifact.scope)

    val depProject = myProjectTree.findProject(artifact.mavenId)

    if (depProject != null) {
      if (depProject === mavenProject) return emptyList()

      val mavenProjectImportData = moduleImportDataByMavenId[depProject.mavenId]

      if (mavenProjectImportData == null || myProjectTree.isIgnored(depProject)) {
        return listOf<MavenImportDependency<*>>(BaseDependency(createCopyForLocalRepo(artifact, mavenProject), scope))
      }
      else {
        val result = ArrayList<MavenImportDependency<*>>()
        val isTestJar = MavenConstants.TYPE_TEST_JAR == dependencyType || "tests" == artifact.classifier
        val moduleName = getModuleName(mavenProjectImportData, isTestJar)

        ContainerUtil.addIfNotNull(result, createAttachArtifactDependency(depProject, scope, artifact))

        val classifier = artifact.classifier
        if (classifier != null && MavenLegacyModuleImporter.IMPORTED_CLASSIFIERS.contains(classifier)
            && !isTestJar
            && "system" != artifact.scope
            && "false" != System.getProperty("idea.maven.classifier.dep")) {
          result.add(LibraryDependency(createCopyForLocalRepo(artifact, mavenProject), mavenProject, scope))
        }

        result.add(ModuleDependency(moduleName, scope, isTestJar))
        return result
      }
    }
    else if ("system" == artifact.scope) {
      return listOf<MavenImportDependency<*>>(SystemDependency(artifact, scope))
    }
    else {
      val finalArtifact = if ("bundle" == dependencyType) {
        MavenArtifact(
          artifact.groupId,
          artifact.artifactId,
          artifact.version,
          artifact.baseVersion,
          "jar",
          artifact.classifier,
          artifact.scope,
          artifact.isOptional,
          "jar",
          null,
          mavenProject.localRepository,
          false, false
        )
      }
      else artifact

      return listOf<MavenImportDependency<*>>(BaseDependency(finalArtifact, scope))
    }
  }

  private fun addMainDependencyToTestModule(importData: MavenProjectImportData,
                                            testDependencies: MutableList<MavenImportDependency<*>>) {
    if (importData.splittedMainAndTestModules != null) {
      testDependencies.add(
        ModuleDependency(importData.splittedMainAndTestModules.mainData.moduleName, DependencyScope.COMPILE, false)
      )
    }
  }

  private fun getModuleName(data: MavenProjectImportData, isTestJar: Boolean): String {
    val modules = data.splittedMainAndTestModules
    if (modules == null) {
      return data.moduleData.moduleName
    }
    return if (isTestJar) modules.testData.moduleName else modules.mainData.moduleName
  }

  private fun createAttachArtifactDependency(mavenProject: MavenProject,
                                             scope: DependencyScope,
                                             artifact: MavenArtifact): AttachedJarDependency? {
    val buildHelperCfg = mavenProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact")
    if (buildHelperCfg == null) return null

    val roots = ArrayList<Pair<String, LibraryRootTypeId>>()
    var create = false

    for (artifactsElement in buildHelperCfg.getChildren("artifacts")) {
      for (artifactElement in artifactsElement.getChildren("artifact")) {
        val typeString = artifactElement.getChildTextTrim("type")
        if (typeString != null && typeString != "jar") continue

        val filePath = artifactElement.getChildTextTrim("file")
        if (StringUtil.isEmpty(filePath)) continue

        val classifier = artifactElement.getChildTextTrim("classifier")
        when (classifier) {
          "sources" -> {
            roots.add(Pair(filePath, SOURCES))
          }
          "javadoc" -> {
            roots.add(Pair(filePath, JAVADOC_TYPE))
          }
          else -> {
            roots.add(Pair(filePath, COMPILED))
          }
        }

        create = true
      }
    }

    return if (create) AttachedJarDependency(getAttachedJarsLibName(artifact), roots, scope) else null
  }
}
