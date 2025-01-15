// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.importing.MavenImportUtil.adjustLevelAndNotify
import org.jetbrains.idea.maven.importing.MavenImportUtil.getSourceLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.getTargetLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.getTestSourceLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.getTestTargetLanguageLevel
import org.jetbrains.idea.maven.importing.MavenImportUtil.hasExecutionsForTests
import org.jetbrains.idea.maven.importing.MavenImportUtil.hasTestCompilerArgs
import org.jetbrains.idea.maven.importing.MavenImportUtil.isCompilerTestSupport
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.*
import java.util.function.Function

private const val MAIN_SUFFIX: String = ".main"
private const val TEST_SUFFIX: String = ".test"

internal class MavenProjectImportContextProvider(
  private val myProject: Project,
  private val myProjectsTree: MavenProjectsTree,
  private val myImportingSettings: MavenImportingSettings,
  private val myMavenProjectToModuleName: Map<MavenProject, String>,
) {

  fun getContext(projectsWithChanges: Map<MavenProject, MavenProjectChanges>): MavenModuleImportContext {
    val importDataContext = getModuleImportDataContext(projectsWithChanges)
    val importDataDependencyContext = getFlattenModuleDataDependencyContext(importDataContext)

    return MavenModuleImportContext(
      importDataDependencyContext.changedModuleDataWithDependencies,
      importDataDependencyContext.allModuleDataWithDependencies,
      importDataContext.moduleNameByProject,
      importDataContext.hasChanges
    )
  }

  private fun getModuleImportDataContext(projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>): ModuleImportDataContext {
    var hasChanges = false
    val allModules: MutableList<MavenProjectImportData> = ArrayList<MavenProjectImportData>()
    val moduleImportDataByMavenId: MutableMap<MavenId, MavenProjectImportData> = TreeMap<MavenId, MavenProjectImportData>(
      Comparator.comparing<MavenId?, String?>(Function { obj: MavenId? -> obj!!.getKey() }))

    for (each in projectsToImportWithChanges.entries) {
      val project = each.key
      val changes = each.value

      val moduleName = getModuleName(project)
      if (StringUtil.isEmpty(moduleName)) {
        MavenLog.LOG.warn("[import context] empty module name for project $project")
        continue
      }

      val mavenProjectImportData = getModuleImportData(project, moduleName!!, changes)
      if (changes.hasChanges()) {
        hasChanges = true
      }
      moduleImportDataByMavenId.put(project.mavenId, mavenProjectImportData)
      allModules.add(mavenProjectImportData)
    }

    return ModuleImportDataContext(allModules, myMavenProjectToModuleName, moduleImportDataByMavenId, hasChanges)
  }

  private fun getModuleName(project: MavenProject): String? {
    return myMavenProjectToModuleName[project]
  }

  private fun getFlattenModuleDataDependencyContext(context: ModuleImportDataContext): ModuleImportDataDependencyContext {
    val allModuleDataWithDependencies: MutableList<MavenTreeModuleImportData> = ArrayList<MavenTreeModuleImportData>()
    val changedModuleDataWithDependencies: MutableList<MavenTreeModuleImportData> = ArrayList<MavenTreeModuleImportData>()

    val dependencyProvider =
      MavenModuleImportDependencyProvider(context.moduleImportDataByMavenId, myImportingSettings, myProjectsTree)

    for (importData in context.importData) {
      val importDataWithDependencies = dependencyProvider.getDependencies(importData)
      val mavenModuleImportDataList = splitToModules(importDataWithDependencies)
      for (moduleImportData in mavenModuleImportDataList) {
        if (moduleImportData.changes.hasChanges()) changedModuleDataWithDependencies.add(moduleImportData)

        allModuleDataWithDependencies.add(moduleImportData)
      }
    }

    return ModuleImportDataDependencyContext(allModuleDataWithDependencies, changedModuleDataWithDependencies)
  }

  private fun getModuleImportData(project: MavenProject, moduleName: String, changes: MavenProjectChanges): MavenProjectImportData {
    val setUpSourceLevel = getSourceLanguageLevel(project)
    val setUpTestSourceLevel = getTestSourceLanguageLevel(project)
    val setUpTargetLevel = getTargetLanguageLevel(project)
    val setUpTestTargetLevel = getTestTargetLanguageLevel(project)

    val sourceLevel = if (setUpSourceLevel == null) null else adjustLevelAndNotify(myProject, setUpSourceLevel)
    val testSourceLevel = if (setUpTestSourceLevel == null) null else adjustLevelAndNotify(myProject, setUpTestSourceLevel)
    val targetLevel = if (setUpTargetLevel == null) null else adjustLevelAndNotify(myProject, setUpTargetLevel)
    val testTargetLevel = if (setUpTestTargetLevel == null) null else adjustLevelAndNotify(myProject, setUpTestTargetLevel)
    val hasExecutionsForTests = hasExecutionsForTests(project)
    val hasTestCompilerArgs = hasTestCompilerArgs(project)

    val needSeparateTestModule =
      hasTestCompilerArgs
      || hasExecutionsForTests
      || (testSourceLevel != null && testSourceLevel != sourceLevel)
      || (testTargetLevel != null && testTargetLevel != targetLevel)

    val needSplitMainAndTest = if (!`is`("maven.import.separate.main.and.test.modules.when.needed")) false
    else !project.isAggregator && needSeparateTestModule && isCompilerTestSupport(project)

    val type = if (needSplitMainAndTest) {
      StandardMavenModuleType.COMPOUND_MODULE
    }
    else if (project.isAggregator) {
      StandardMavenModuleType.AGGREGATOR
    }
    else {
      StandardMavenModuleType.SINGLE_MODULE
    }

    val moduleData = ModuleData(moduleName, type, sourceLevel, testSourceLevel)
    if (type != StandardMavenModuleType.COMPOUND_MODULE) {
      return MavenProjectImportData(project, moduleData, changes, listOf())
    }
    val moduleMainName = moduleName + MAIN_SUFFIX
    val mainData = ModuleData(moduleMainName, StandardMavenModuleType.MAIN_ONLY, sourceLevel, testSourceLevel)

    val moduleTestName = moduleName + TEST_SUFFIX
    val testData = ModuleData(moduleTestName, StandardMavenModuleType.TEST_ONLY, sourceLevel, testSourceLevel)

    return MavenProjectImportData(project, moduleData, changes, listOf(mainData, testData))
  }

  private class ModuleImportDataContext(
    val importData: List<MavenProjectImportData>,
    val moduleNameByProject: Map<MavenProject, String>,
    val moduleImportDataByMavenId: Map<MavenId, MavenProjectImportData>,
    val hasChanges: Boolean,
  )

  private class ModuleImportDataDependencyContext(
    val allModuleDataWithDependencies: List<MavenTreeModuleImportData>,
    val changedModuleDataWithDependencies: List<MavenTreeModuleImportData>,
  )

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
        if (anotherModuleData.type == StandardMavenModuleType.MAIN_ONLY) {
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


