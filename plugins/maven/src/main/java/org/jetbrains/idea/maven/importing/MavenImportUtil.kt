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
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.storage.entities
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.pom.java.LanguageLevel.HIGHEST
import com.intellij.util.text.VersionComparatorUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Supplier

@ApiStatus.Internal
object MavenImportUtil {
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

  fun getSourceLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), true, false)
  }

  fun getTestSourceLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), true, true)
  }

  fun getTargetLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), false, false)
  }

  fun getTestTargetLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
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
    return level
  }

  internal fun getMaxMavenJavaVersion(projects: List<MavenProject>): LanguageLevel? {
    val maxLevel = projects.flatMap {
      listOf(
        getSourceLanguageLevel(it),
        getTestSourceLanguageLevel(it),
        getTargetLanguageLevel(it),
        getTestTargetLanguageLevel(it)
      )
    }.filterNotNull().maxWithOrNull(Comparator.naturalOrder()) ?: HIGHEST
    return maxLevel
  }

  internal fun hasTestCompilerArgs(project: MavenProject): Boolean {
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

  internal fun hasExecutionsForTests(project: MavenProject): Boolean {
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

  private fun adjustPreviewLanguageLevel(mavenProject: MavenProject, level: LanguageLevel): LanguageLevel {
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

  private fun isReleaseCompilerProp(mavenProject: MavenProject): Boolean {
    return StringUtil.compareVersionNumbers(MavenUtil.getCompilerPluginVersion(mavenProject), "3.6") >= 0
  }

  fun isCompilerTestSupport(mavenProject: MavenProject): Boolean {
    return StringUtil.compareVersionNumbers(MavenUtil.getCompilerPluginVersion(mavenProject), "2.1") >= 0
  }

  fun isMainOrTestModule(project: Project, moduleName: String): Boolean {
    val type = getMavenModuleType(project, moduleName)
    return type == StandardMavenModuleType.MAIN_ONLY || type == StandardMavenModuleType.TEST_ONLY
  }

  fun isTestModule(project: Project, moduleName: String): Boolean {
    val type = getMavenModuleType(project, moduleName)
    return type == StandardMavenModuleType.TEST_ONLY
  }

  @JvmStatic
  fun findPomXml(module: Module): VirtualFile? {
    val project = module.project
    val storage = project.workspaceModel.currentSnapshot
    val pomPath = storage.resolve(ModuleId(module.name))?.exModuleOptions?.linkedProjectId?.toNioPathOrNull() ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(pomPath)
  }

  fun getMavenModuleType(project: Project, moduleName: @NlsSafe String): StandardMavenModuleType {
    val storage = project.workspaceModel.currentSnapshot
    val default = StandardMavenModuleType.SINGLE_MODULE
    val moduleTypeString = storage.resolve(ModuleId(moduleName))?.exModuleOptions?.externalSystemModuleType ?: return default
    return try {
      enumValueOf<StandardMavenModuleType>(moduleTypeString)
    }
    catch (_: IllegalArgumentException) {
      MavenLog.LOG.warn("Unknown module type: $moduleTypeString")
      default
    }
  }

  fun getModuleNames(project: Project, pomXml: VirtualFile): List<String> {
    val storage = project.workspaceModel.currentSnapshot
    val pomXmlPath = pomXml.toNioPath()
    return storage.entities<ModuleEntity>()
      .filter { it.exModuleOptions?.linkedProjectId?.toNioPathOrNull() == pomXmlPath }
      .map { it.name }
      .toList()
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
}
