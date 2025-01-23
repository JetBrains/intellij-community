// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectModifications

internal class ModuleData(
  val moduleName: String,
  val type: StandardMavenModuleType,
  val sourceLanguageLevel: LanguageLevel?,
) {
  val isDefaultMainModule = type == StandardMavenModuleType.MAIN_ONLY
  val isAdditionalMainModule = type == StandardMavenModuleType.MAIN_ONLY_ADDITIONAL
  val isMainModule = isDefaultMainModule || isAdditionalMainModule
  val isTestModule = type == StandardMavenModuleType.TEST_ONLY
  val isMainOrTestModule = isMainModule || isTestModule

  override fun toString(): String {
    return moduleName
  }
}

internal open class MavenModuleImportData(val mavenProject: MavenProject, val moduleData: ModuleData) {
  override fun toString(): String {
    return moduleData.moduleName
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    val data = o as MavenModuleImportData
    return moduleData.moduleName == data.moduleData.moduleName
  }

  override fun hashCode(): Int {
    return moduleData.moduleName.hashCode()
  }
}

internal open class MavenTreeModuleImportData(
  mavenProject: MavenProject,
  moduleData: ModuleData,
  val dependencies: List<MavenImportDependency<*>>,
  val changes: MavenProjectModifications,
) : MavenModuleImportData(mavenProject, moduleData)
