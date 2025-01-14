// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.events.MessageEvent
import com.intellij.execution.configurations.JavaParameters
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.VersionComparatorUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.tree.MavenJavaVersionHolder
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Supplier

@ApiStatus.Internal
object MavenImportUtil {
  const val TEST_SUFFIX: String = ".test"
  const val MAIN_SUFFIX: String = ".main"

  private val MAVEN_IDEA_PLUGIN_LEVELS = mapOf(
    "JDK_1_3" to LanguageLevel.JDK_1_3,
    "JDK_1_4" to LanguageLevel.JDK_1_4,
    "JDK_1_5" to LanguageLevel.JDK_1_5,
    "JDK_1_6" to LanguageLevel.JDK_1_6,
    "JDK_1_7" to LanguageLevel.JDK_1_7
  )

  fun getArtifactUrlForClassifierAndExtension(
    artifact: MavenArtifact,
    classifier: String?,
    extension: String?,
  ): String {
    val newPath = artifact.getPathForExtraArtifact(classifier, extension)
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, newPath) + JarFileSystem.JAR_SEPARATOR
  }

  fun getTargetLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), false, false)
  }

  fun getTargetTestLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), false, true)
  }

  fun getLanguageLevel(mavenProject: MavenProject, supplier: Supplier<LanguageLevel?>): LanguageLevel {
    var level: LanguageLevel? = null

    val cfg = mavenProject.getPluginConfiguration("com.googlecode", "maven-idea-plugin")
    if (cfg != null) {
      val key = cfg.getChildTextTrim("jdkLevel")
      level = if (key == null) null else MAVEN_IDEA_PLUGIN_LEVELS[key]
    }

    if (level == null) {
      level = supplier.get()
    }

    // default source and target settings of maven-compiler-plugin is 1.5 for versions less than 3.8.1 and 1.6 for 3.8.1 and above
    // see details at http://maven.apache.org/plugins/maven-compiler-plugin and https://issues.apache.org/jira/browse/MCOMPILER-335
    if (level == null) {
      level = getDefaultLevel(mavenProject)
    }

    if (level.isAtLeast(LanguageLevel.JDK_11)) {
      level = adjustPreviewLanguageLevel(mavenProject, level)
    }
    return level!!
  }

  @Deprecated("Maven project can have multiple source/target versions if multiReleaseOutput is used")
  fun getMavenJavaVersions(mavenProject: MavenProject): MavenJavaVersionHolder {
    val useReleaseCompilerProp = isReleaseCompilerProp(mavenProject)
    val sourceVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, true, false)
    val sourceTestVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, true, true)
    val targetVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, false, false)
    val targetTestVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, false, true)
    return MavenJavaVersionHolder(sourceVersion, targetVersion, sourceTestVersion, targetTestVersion,
                                  hasAnotherTestExecution(mavenProject),
                                  hasTestCompilerArgs(mavenProject))
  }

  private fun hasTestCompilerArgs(project: MavenProject): Boolean {
    val plugin = project.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin") ?: return false
    val executions = plugin.executions
    if (executions == null || executions.isEmpty()) {
      return hasTestCompilerArgs(plugin.configurationElement)
    }

    return executions.any { hasTestCompilerArgs(it.configurationElement) }
  }

  private fun hasTestCompilerArgs(config: Element?): Boolean {
    return config != null && (config.getChild("testCompilerArgument") != null ||
                              config.getChild("testCompilerArguments") != null)
  }

  private fun hasAnotherTestExecution(project: MavenProject): Boolean {
    val plugin = project.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")
    if (plugin == null) return false
    val executions = plugin.executions
    if (executions == null || executions.isEmpty()) return false
    val compileExec = executions.find { isCompileExecution(it) }
    val testExec = executions.find { isTestExecution(it) }
    if (compileExec == null) return testExec != null
    if (testExec == null) return true
    return !JDOMUtil.areElementsEqual(compileExec.configurationElement, testExec.configurationElement)
  }

  fun isTestExecution(e: MavenPlugin.Execution): Boolean {
    return checkExecution(e, "test-compile", "test-compile", "default-testCompile")
  }


  private fun isCompileExecution(e: MavenPlugin.Execution): Boolean {
    return checkExecution(e, "compile", "compile", "default-compile")
  }

  private fun checkExecution(e: MavenPlugin.Execution, phase: String, goal: String?, defaultExecId: String): Boolean {
    return "none" != e.phase &&
           (phase == e.phase ||
            (e.goals != null && e.goals.contains(goal)) ||
            (defaultExecId == e.executionId)
           )
  }

  private fun getMavenLanguageLevel(
    mavenProject: MavenProject,
    useReleaseCompilerProp: Boolean,
    isSource: Boolean,
    isTest: Boolean,
  ): LanguageLevel? {
    val mavenProjectReleaseLevel = if (useReleaseCompilerProp)
      if (isTest) mavenProject.testReleaseLevel else mavenProject.releaseLevel
    else
      null
    var level = LanguageLevel.parse(mavenProjectReleaseLevel)
    if (level == null) {
      val mavenProjectLanguageLevel = getMavenLanguageLevel(mavenProject, isTest, isSource)
      level = LanguageLevel.parse(mavenProjectLanguageLevel)
      if (level == null && (StringUtil.isNotEmpty(mavenProjectLanguageLevel) || StringUtil.isNotEmpty(mavenProjectReleaseLevel))) {
        level = LanguageLevel.HIGHEST
      }
    }
    return level
  }

  @JvmStatic
  fun adjustLevelAndNotify(project: Project, level: LanguageLevel): LanguageLevel {
    var level = level
    if (!AcceptedLanguageLevelsSettings.isLanguageLevelAccepted(level)) {
      val highestAcceptedLevel = AcceptedLanguageLevelsSettings.getHighestAcceptedLevel()
      if (highestAcceptedLevel.isLessThan(level)) {
        MavenProjectsManager.getInstance(project).getSyncConsole().addBuildIssue(NonAcceptedJavaLevelIssue(level), MessageEvent.Kind.WARNING)
      }
      level = if (highestAcceptedLevel.isAtLeast(level)) LanguageLevel.HIGHEST else highestAcceptedLevel
    }
    return level
  }

  private fun getMavenLanguageLevel(project: MavenProject, test: Boolean, source: Boolean): String? {
    if (test) {
      return if (source) project.testSourceLevel else project.testTargetLevel
    }
    else {
      return if (source) project.sourceLevel else project.targetLevel
    }
  }

  fun getDefaultLevel(mavenProject: MavenProject): LanguageLevel {
    val plugin = mavenProject.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")
    if (plugin != null && plugin.version != null) {
      //https://github.com/apache/maven-compiler-plugin/blob/master/src/main/java/org/apache/maven/plugin/compiler/AbstractCompilerMojo.java
      // consider "source" parameter documentation.
      // also note, that these are versions of plugin, not maven.
      if (VersionComparatorUtil.compare("3.11.0", plugin.version) <= 0) {
        return LanguageLevel.JDK_1_8
      }
      if (VersionComparatorUtil.compare("3.9.0", plugin.version) <= 0) {
        return LanguageLevel.JDK_1_7
      }
      if (VersionComparatorUtil.compare("3.8.0", plugin.version) <= 0) {
        return LanguageLevel.JDK_1_6
      }
      else {
        return LanguageLevel.JDK_1_5
      }
    }
    return LanguageLevel.JDK_1_5
  }

  private fun adjustPreviewLanguageLevel(mavenProject: MavenProject, level: LanguageLevel): LanguageLevel? {
    val enablePreviewProperty = mavenProject.properties.getProperty("maven.compiler.enablePreview")
    if (enablePreviewProperty.toBoolean()) {
      return level.getPreviewLevel() ?: level
    }

    val compilerConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin")
    if (compilerConfiguration != null) {
      val enablePreviewParameter = compilerConfiguration.getChildTextTrim("enablePreview")
      if (enablePreviewParameter.toBoolean()) {
        return level.getPreviewLevel() ?: level
      }

      val compilerArgs = compilerConfiguration.getChild("compilerArgs")
      if (compilerArgs != null) {
        if (isPreviewText(compilerArgs) ||
            compilerArgs.getChildren("arg").any { isPreviewText(it) } ||
            compilerArgs.getChildren("compilerArg").any{ isPreviewText(it) }
        ) {
          return level.getPreviewLevel() ?: level
        }
      }
    }

    return level
  }

  fun isPreviewText(child: Element): Boolean {
    return JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY == child.textTrim
  }

  fun isReleaseCompilerProp(mavenProject: MavenProject): Boolean {
    return StringUtil.compareVersionNumbers(MavenUtil.getCompilerPluginVersion(mavenProject), "3.6") >= 0
  }

  fun isCompilerTestSupport(mavenProject: MavenProject): Boolean {
    return StringUtil.compareVersionNumbers(MavenUtil.getCompilerPluginVersion(mavenProject), "2.1") >= 0
  }

  @JvmStatic
  fun isMainOrTestSubmodule(moduleName: String): Boolean {
    return isMainModule(moduleName) || isTestModule(moduleName)
  }

  fun isMainModule(moduleName: String): Boolean {
    return moduleName.length > 5 && moduleName.endsWith(MAIN_SUFFIX)
  }

  fun isTestModule(moduleName: String): Boolean {
    return moduleName.length > 5 && moduleName.endsWith(TEST_SUFFIX)
  }

  @JvmStatic
  fun getParentModuleName(moduleName: String): String {
    if (isMainModule(moduleName)) {
      return moduleName.removeSuffix(MAIN_SUFFIX)
    }
    if (isTestModule(moduleName)) {
      return moduleName.removeSuffix(TEST_SUFFIX)
    }
    return moduleName
  }

  fun createPreviewModule(project: Project, contentRoot: VirtualFile): Module? {
    return WriteAction.compute<Module?, RuntimeException?>(ThrowableComputable {
      val modulePath = contentRoot.toNioPath().resolve(project.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION)
      val module = getInstance(project)
        .newModule(modulePath, ModuleTypeManager.getInstance().getDefaultModuleType().id)
      val modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel()
      modifiableModel.addContentEntry(contentRoot)
      modifiableModel.commit()

      ExternalSystemUtil.markModuleAsMaven(module, null, true)
      module
    })
  }

  private fun needSplitMainAndTest(project: MavenProject, mavenJavaVersions: MavenJavaVersionHolder): Boolean {
    if (!`is`("maven.import.separate.main.and.test.modules.when.needed")) return false
    return !project.isAggregator && mavenJavaVersions.needSeparateTestModule() && isCompilerTestSupport(project)
  }

  @JvmStatic
  @ApiStatus.Internal
  fun getModuleType(project: MavenProject, mavenJavaVersions: MavenJavaVersionHolder): StandardMavenModuleType {
    if (needSplitMainAndTest(project, mavenJavaVersions)) {
      return StandardMavenModuleType.COMPOUND_MODULE
    }
    else if (project.isAggregator) {
      return StandardMavenModuleType.AGGREGATOR
    }
    else {
      return StandardMavenModuleType.SINGLE_MODULE
    }
  }
}
