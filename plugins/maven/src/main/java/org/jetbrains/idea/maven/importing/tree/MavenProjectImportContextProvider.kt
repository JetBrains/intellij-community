// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId.Companion.COMPILED
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId.Companion.SOURCES
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.maven.importing.MavenImportUtil.MAIN_SUFFIX
import org.jetbrains.idea.maven.importing.MavenImportUtil.TEST_SUFFIX
import org.jetbrains.idea.maven.importing.MavenImportUtil.adjustLevelAndNotify
import org.jetbrains.idea.maven.importing.MavenImportUtil.escapeCompileSourceRootModuleSuffix
import org.jetbrains.idea.maven.importing.MavenImportUtil.getNonDefaultCompilerExecutions
import org.jetbrains.idea.maven.importing.MavenImportUtil.getSourceLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.getTargetLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.getTestSourceLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.getTestTargetLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.hasExecutionsForTests
import org.jetbrains.idea.maven.importing.MavenImportUtil.hasTestCompilerArgs
import org.jetbrains.idea.maven.importing.MavenImportUtil.isCompilerTestSupport
import org.jetbrains.idea.maven.importing.MavenProjectImporterUtil.selectScope
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.idea.maven.importing.tree.dependency.*
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter.Companion.JAVADOC_TYPE
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectModifications
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.SupportedRequestType
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.*
import java.util.function.Function

private const val INITIAL_CAPACITY_TEST_DEPENDENCY_LIST: Int = 4
private val IMPORTED_CLASSIFIERS = setOf("client")

internal class MavenProjectImportContextProvider(
  private val myProject: Project,
  private val myProjectsTree: MavenProjectsTree,
  private val dependencyTypes: Set<String>,
  private val myMavenProjectToModuleName: Map<MavenProject, String>,
) {

  fun getAllModules(projectsWithChanges: Map<MavenProject, MavenProjectModifications>): List<MavenTreeModuleImportData> {
    val importDataContext = getModuleImportDataContext(projectsWithChanges)
    return getFlattenModuleDataDependencyContext(importDataContext)
  }

  private fun getModuleImportDataContext(projectsToImportWithChanges: Map<MavenProject, MavenProjectModifications>): ModuleImportDataContext {
    val allModules: MutableList<MavenProjectImportData> = ArrayList<MavenProjectImportData>()
    val moduleImportDataByMavenId: MutableMap<MavenId, MavenProjectImportData> = TreeMap<MavenId, MavenProjectImportData>(
      Comparator.comparing<MavenId?, String?>(Function { obj: MavenId? -> obj!!.getKey() }))

    for (each in projectsToImportWithChanges.entries) {
      val project = each.key
      val changes = each.value

      val moduleName = myMavenProjectToModuleName[project]
      if (StringUtil.isEmpty(moduleName)) {
        MavenLog.LOG.warn("[import context] empty module name for project $project")
        continue
      }

      val mavenProjectImportData = getModuleImportData(project, moduleName!!, changes)
      moduleImportDataByMavenId.put(project.mavenId, mavenProjectImportData)
      allModules.add(mavenProjectImportData)
    }

    return ModuleImportDataContext(allModules, moduleImportDataByMavenId)
  }

  private fun getFlattenModuleDataDependencyContext(context: ModuleImportDataContext): List<MavenTreeModuleImportData> {
    val allModuleDataWithDependencies: MutableList<MavenTreeModuleImportData> = ArrayList<MavenTreeModuleImportData>()

    for (importData in context.importData) {
      val importDataWithDependencies = getDependencies(context.moduleImportDataByMavenId, importData)
      val mavenModuleImportDataList = splitToModules(importDataWithDependencies)
      for (moduleImportData in mavenModuleImportDataList) {
        allModuleDataWithDependencies.add(moduleImportData)
      }
    }

    return allModuleDataWithDependencies
  }

  private fun getDependencies(moduleImportDataByMavenId: Map<MavenId, MavenProjectImportData>, importData: MavenProjectImportData): MavenModuleImportDataWithDependencies {
    val mavenProject = importData.mavenProject

    MavenLog.LOG.debug("Creating dependencies for $mavenProject: ${mavenProject.dependencies.size}")

    val mainDependencies: MutableList<MavenImportDependency<*>> = ArrayList(mavenProject.dependencies.size)
    val testDependencies: MutableList<MavenImportDependency<*>> = ArrayList(INITIAL_CAPACITY_TEST_DEPENDENCY_LIST)

    addMainDependencyToTestModule(importData, testDependencies)
    val otherTestModules = importData.otherTestModules
    for (artifact in mavenProject.dependencies) {
      for (dependency in getDependency(moduleImportDataByMavenId, artifact, mavenProject)) {
        if (otherTestModules.isNotEmpty() && dependency.scope == DependencyScope.TEST) {
          testDependencies.add(dependency)
        }
        else {
          mainDependencies.add(dependency)
        }
      }
    }
    return MavenModuleImportDataWithDependencies(importData, mainDependencies, testDependencies)
  }

  private fun getDependency(moduleImportDataByMavenId: Map<MavenId, MavenProjectImportData>, artifact: MavenArtifact, mavenProject: MavenProject): List<MavenImportDependency<*>> {
    val dependencyType = artifact.type
    MavenLog.LOG.trace("Creating dependency from $mavenProject to $artifact, type $dependencyType")

    if (!dependencyTypes.contains(dependencyType)
        && !mavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType)) {
      MavenLog.LOG.trace("Dependency skipped")
      return emptyList()
    }

    val scope = selectScope(artifact.scope)

    val depProject = myProjectsTree.findProject(artifact.mavenId)

    if (depProject != null) {
      MavenLog.LOG.trace("Dependency project $depProject")

      if (depProject === mavenProject) {
        MavenLog.LOG.trace("Project depends on itself")
        return emptyList()
      }

      val mavenProjectImportData = moduleImportDataByMavenId[depProject.mavenId]

      val depProjectIgnored = myProjectsTree.isIgnored(depProject)
      if (mavenProjectImportData == null || depProjectIgnored) {
        MavenLog.LOG.trace("Created base dependency, project ignored: $depProjectIgnored, import data: $mavenProjectImportData")
        return listOf<MavenImportDependency<*>>(BaseDependency(createCopyForLocalRepo(artifact, mavenProject), scope))
      }
      else {
        val result = ArrayList<MavenImportDependency<*>>()
        val isTestJar = MavenConstants.TYPE_TEST_JAR == dependencyType || "tests" == artifact.classifier
        val moduleName = getModuleName(mavenProjectImportData, isTestJar)

        ContainerUtil.addIfNotNull(result, createAttachArtifactDependency(depProject, scope, artifact))

        val classifier = artifact.classifier
        if (classifier != null && IMPORTED_CLASSIFIERS.contains(classifier)
            && !isTestJar
            && "system" != artifact.scope
            && "false" != System.getProperty("idea.maven.classifier.dep")) {
          MavenLog.LOG.trace("Created library dependency")
          result.add(LibraryDependency(createCopyForLocalRepo(artifact, mavenProject), mavenProject, scope))
        }

        MavenLog.LOG.trace("Created module dependency")
        result.add(ModuleDependency(moduleName, scope, isTestJar))
        return result
      }
    }
    else if ("system" == artifact.scope) {
      MavenLog.LOG.trace("Created system dependency")
      return listOf<MavenImportDependency<*>>(SystemDependency(artifact, scope))
    }
    else {
      val isBundle = "bundle" == dependencyType
      val finalArtifact = if (isBundle) {
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
          mavenProject.localRepositoryPath.toFile(),
          false, false
        )
      }
      else artifact

      MavenLog.LOG.trace("Created base dependency, bundle: $isBundle")
      return listOf<MavenImportDependency<*>>(BaseDependency(finalArtifact, scope))
    }
  }

  private fun createCopyForLocalRepo(artifact: MavenArtifact, project: MavenProject): MavenArtifact {
    return MavenArtifact(
      artifact.groupId,
      artifact.artifactId,
      artifact.version,
      artifact.baseVersion,
      artifact.type,
      artifact.classifier,
      artifact.scope,
      artifact.isOptional,
      artifact.extension,
      null,
      project.localRepositoryPath.toFile(),
      false, false
    )
  }

  private fun addMainDependencyToTestModule(importData: MavenProjectImportData,
                                            testDependencies: MutableList<MavenImportDependency<*>>) {
    importData.otherMainModules.forEach {
      testDependencies.add(
        ModuleDependency(it.moduleName, DependencyScope.COMPILE, false)
      )
    }
  }

  private fun getModuleName(data: MavenProjectImportData, isTestJar: Boolean): String {
    val otherModule = if (isTestJar) data.otherTestModules.firstOrNull() else data.otherMainModules.firstOrNull()
    return otherModule?.moduleName ?: data.moduleData.moduleName
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

  private fun getAttachedJarsLibName(artifact: MavenArtifact): String {
    var libraryName = artifact.getLibraryName()
    assert(libraryName.startsWith(MavenArtifact.MAVEN_LIB_PREFIX))
    libraryName = MavenArtifact.MAVEN_LIB_PREFIX + "ATTACHED-JAR: " + libraryName.substring(MavenArtifact.MAVEN_LIB_PREFIX.length)
    return libraryName
  }

  private data class LanguageLevels(
    val sourceLevel: LanguageLevel?,
    val testSourceLevel: LanguageLevel?,
    val targetLevel: LanguageLevel?,
    val testTargetLevel: LanguageLevel?,
  ) {
    fun mainAndTestLevelsDiffer(): Boolean {
      return (testSourceLevel != null && testSourceLevel != sourceLevel)
             || (testTargetLevel != null && testTargetLevel != targetLevel)
    }
  }

  private fun getLanguageLevels(project: MavenProject): LanguageLevels {
    val setUpSourceLevel = getSourceLanguageLevel(project)
    val setUpTestSourceLevel = getTestSourceLanguageLevel(project)
    val setUpTargetLevel = getTargetLanguageLevel(project)
    val setUpTestTargetLevel = getTestTargetLanguageLevel(project)

    val sourceLevel = if (setUpSourceLevel == null) null else adjustLevelAndNotify(myProject, setUpSourceLevel)
    val testSourceLevel = if (setUpTestSourceLevel == null) null else adjustLevelAndNotify(myProject, setUpTestSourceLevel)
    val targetLevel = if (setUpTargetLevel == null) null else adjustLevelAndNotify(myProject, setUpTargetLevel)
    val testTargetLevel = if (setUpTestTargetLevel == null) null else adjustLevelAndNotify(myProject, setUpTestTargetLevel)

    return LanguageLevels(sourceLevel, testSourceLevel, targetLevel, testTargetLevel)
  }

  private fun needCreateCompoundModule(project: MavenProject, languageLevels: LanguageLevels): Boolean {
    if (!`is`("maven.import.separate.main.and.test.modules.when.needed")) return false
    if (project.isAggregator) return false
    if (!isCompilerTestSupport(project)) return false

    if (languageLevels.mainAndTestLevelsDiffer()) return true
    if (hasTestCompilerArgs(project)) return true
    if (hasExecutionsForTests(project)) return true
    if (getNonDefaultCompilerExecutions(project).isNotEmpty()) return true

    return false
  }

  private fun getModuleImportDataSingle(
    project: MavenProject,
    moduleName: String,
    changes: MavenProjectModifications,
    languageLevels: LanguageLevels,
  ): MavenProjectImportData {
    val type = if (project.isAggregator) {
      StandardMavenModuleType.AGGREGATOR
    }
    else {
      StandardMavenModuleType.SINGLE_MODULE
    }

    val sourceLevel = languageLevels.sourceLevel
    val testSourceLevel = languageLevels.testSourceLevel
    val moduleData = ModuleData(moduleName, type, sourceLevel, testSourceLevel)
    return MavenProjectImportData(project, moduleData, changes, listOf())
  }

  private fun getModuleImportDataCompound(
    project: MavenProject,
    moduleName: String,
    changes: MavenProjectModifications,
    languageLevels: LanguageLevels,
  ): MavenProjectImportData {
    val type = StandardMavenModuleType.COMPOUND_MODULE

    val sourceLevel = languageLevels.sourceLevel
    val testSourceLevel = languageLevels.testSourceLevel
    val moduleData = ModuleData(moduleName, type, sourceLevel, testSourceLevel)

    val moduleMainName = "$moduleName.$MAIN_SUFFIX"
    val mainData = ModuleData(moduleMainName, StandardMavenModuleType.MAIN_ONLY, sourceLevel, testSourceLevel)

    val moduleTestName = "$moduleName.$TEST_SUFFIX"
    val testData = ModuleData(moduleTestName, StandardMavenModuleType.TEST_ONLY, sourceLevel, testSourceLevel)

    val compileSourceRootModules = getNonDefaultCompilerExecutions(project).map {
      val suffix = escapeCompileSourceRootModuleSuffix(it)
      ModuleData("$moduleName.$suffix", StandardMavenModuleType.MAIN_ONLY_ADDITIONAL, sourceLevel, testSourceLevel)
    }

    val otherModules = listOf(mainData) + compileSourceRootModules + testData
    return MavenProjectImportData(project, moduleData, changes, otherModules)
  }

  private fun getModuleImportData(project: MavenProject, moduleName: String, changes: MavenProjectModifications): MavenProjectImportData {
    val languageLevels = getLanguageLevels(project)

    val needCreateCompoundModule = needCreateCompoundModule(project, languageLevels)

    if (needCreateCompoundModule) {
      return getModuleImportDataCompound(project, moduleName, changes, languageLevels)
    }
    else {
      return getModuleImportDataSingle(project, moduleName, changes, languageLevels)
    }
  }

  private fun splitToModules(dataWithDependencies: MavenModuleImportDataWithDependencies): List<MavenTreeModuleImportData> {
    val otherModules = dataWithDependencies.moduleImportData.otherModules
    val project = dataWithDependencies.moduleImportData.mavenProject
    val moduleData = dataWithDependencies.moduleImportData.moduleData
    val changes = dataWithDependencies.moduleImportData.changes

    if (otherModules.isNotEmpty()) {
      val result = ArrayList<MavenTreeModuleImportData>(3)
      result.add(MavenTreeModuleImportData(
        project, moduleData, mutableListOf<MavenImportDependency<*>>(), dataWithDependencies.moduleImportData.changes
      ))
      val dependencies = dataWithDependencies.testDependencies + dataWithDependencies.mainDependencies

      for (anotherModuleData in otherModules) {
        if (anotherModuleData.type == StandardMavenModuleType.MAIN_ONLY || anotherModuleData.type == StandardMavenModuleType.MAIN_ONLY_ADDITIONAL) {
          result.add(MavenTreeModuleImportData(project, anotherModuleData, dataWithDependencies.mainDependencies, changes))
        }
        if (anotherModuleData.type == StandardMavenModuleType.TEST_ONLY) {
          result.add(MavenTreeModuleImportData(project, anotherModuleData, dependencies, changes))
        }
      }

      return result
    }

    return listOf(MavenTreeModuleImportData(
      project, moduleData,
      dataWithDependencies.mainDependencies + dataWithDependencies.testDependencies,
      changes
    ))
  }
}

private class ModuleImportDataContext(
  val importData: List<MavenProjectImportData>,
  val moduleImportDataByMavenId: Map<MavenId, MavenProjectImportData>,
)

private class MavenModuleImportDataWithDependencies(
  val moduleImportData: MavenProjectImportData,
  val mainDependencies: List<MavenImportDependency<*>>,
  val testDependencies: List<MavenImportDependency<*>> = emptyList(),
) {
  override fun toString(): String {
    return moduleImportData.mavenProject.mavenId.toString()
  }
}

private class MavenProjectImportData(
  val mavenProject: MavenProject,
  val moduleData: ModuleData,
  val changes: MavenProjectModifications,
  val otherModules: List<ModuleData>,
) {

  val otherMainModules = otherModules.filter { it.type == StandardMavenModuleType.MAIN_ONLY || it.type == StandardMavenModuleType.MAIN_ONLY_ADDITIONAL }
  val otherTestModules = otherModules.filter { it.type == StandardMavenModuleType.TEST_ONLY }

  override fun toString(): String {
    return mavenProject.mavenId.toString()
  }
}
