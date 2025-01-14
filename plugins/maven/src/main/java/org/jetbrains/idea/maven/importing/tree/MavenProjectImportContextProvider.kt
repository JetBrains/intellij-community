// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.importing.MavenImportUtil.adjustLevelAndNotify
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
    val setUpJavaVersions = MavenImportUtil.getMavenJavaVersions(project)
    val javaVersions = adjustJavaVersions(setUpJavaVersions)

    val type = MavenImportUtil.getModuleType(project, javaVersions)

    val moduleData = ModuleData(moduleName, type, javaVersions)
    if (type != StandardMavenModuleType.COMPOUND_MODULE) {
      return MavenProjectImportData(project, moduleData, changes, listOf())
    }
    val moduleMainName = moduleName + MavenImportUtil.MAIN_SUFFIX
    val mainData = ModuleData(moduleMainName, StandardMavenModuleType.MAIN_ONLY, javaVersions)

    val moduleTestName = moduleName + MavenImportUtil.TEST_SUFFIX
    val testData = ModuleData(moduleTestName, StandardMavenModuleType.TEST_ONLY, javaVersions)

    return MavenProjectImportData(project, moduleData, changes, listOf(mainData, testData))
  }

  private fun adjustJavaVersions(holder: MavenJavaVersionHolder): MavenJavaVersionHolder {
    return MavenJavaVersionHolder(
      if (holder.sourceLevel == null) null else adjustLevelAndNotify(myProject, holder.sourceLevel),
      if (holder.targetLevel == null) null else adjustLevelAndNotify(myProject, holder.targetLevel),
      if (holder.testSourceLevel == null) null else adjustLevelAndNotify(myProject, holder.testSourceLevel),
      if (holder.testTargetLevel == null) null else adjustLevelAndNotify(myProject, holder.testTargetLevel),
      holder.hasExecutionsForTests,
      holder.hasTestCompilerArgs
    )
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


