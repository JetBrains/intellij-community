// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.openapi.module.Module
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges

open class ModuleData(val moduleName: String,
                      val type: StandardMavenModuleType,
                      private val javaVersionHolder: MavenJavaVersionHolder) {
  val sourceLanguageLevel: LanguageLevel?
    get() = if (type == StandardMavenModuleType.TEST_ONLY) javaVersionHolder.testSourceLevel else javaVersionHolder.sourceLevel
  val targetLanguageLevel: LanguageLevel?
    get() = if (type == StandardMavenModuleType.TEST_ONLY) javaVersionHolder.testTargetLevel else javaVersionHolder.targetLevel

  override fun toString(): String {
    return moduleName
  }
}

class LegacyModuleData(val module: Module,
                       type: StandardMavenModuleType,
                       javaVersionHolder: MavenJavaVersionHolder,
                       val isNewModule: Boolean) : ModuleData(module.name, type, javaVersionHolder)

open class MavenModuleImportData(val mavenProject: MavenProject,
                                 val moduleData: ModuleData) {
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

open class MavenTreeModuleImportData(mavenProject: MavenProject,
                                     moduleData: ModuleData,
                                     val dependencies: List<MavenImportDependency<*>>,
                                     val changes: MavenProjectChanges) : MavenModuleImportData(mavenProject, moduleData) {

  val legacyModuleData: LegacyModuleData
    get() = moduleData as LegacyModuleData

}

class MavenProjectImportData(val mavenProject: MavenProject,
                             val moduleData: ModuleData,
                             val changes: MavenProjectChanges,
                             val splittedMainAndTestModules: SplittedMainAndTestModules?) {

  override fun toString(): String {
    return mavenProject.mavenId.toString()
  }
}

class SplittedMainAndTestModules(val mainData: ModuleData, val testData: ModuleData)

class MavenModuleImportDataWithDependencies @JvmOverloads constructor(val moduleImportData: MavenProjectImportData,
                                                                      val mainDependencies: List<MavenImportDependency<*>>,
                                                                      val testDependencies: List<MavenImportDependency<*>> = emptyList()) {

  override fun toString(): String {
    return moduleImportData.mavenProject.mavenId.toString()
  }
}
