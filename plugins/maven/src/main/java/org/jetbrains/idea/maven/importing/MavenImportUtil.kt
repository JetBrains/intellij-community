// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.events.MessageEvent
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry.Companion.`is`
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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProject.ProcMode
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildValueByPath
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.PrefixStringEncoder
import java.io.File
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

  private const val COMPILER_PLUGIN_GROUP_ID = "org.apache.maven.plugins"
  private const val COMPILER_PLUGIN_ARTIFACT_ID = "maven-compiler-plugin"

  internal const val MAIN_SUFFIX: String = "main"
  internal const val TEST_SUFFIX: String = "test"

  // compileSourceRoot submodules cannot be named 'main' and 'test'
  private val compileSourceRootEncoder = PrefixStringEncoder(setOf(MAIN_SUFFIX, TEST_SUFFIX), "compileSourceRoot-")

  private const val PHASE_COMPILE = "compile"
  private const val PHASE_TEST_COMPILE = "test-compile"

  private const val GOAL_COMPILE = "compile"
  private const val GOAL_TEST_COMPILE = "testCompile"

  private const val EXECUTION_COMPILE = "default-compile"
  private const val EXECUTION_TEST_COMPILE = "default-testCompile"

  internal fun getArtifactUrlForClassifierAndExtension(artifact: MavenArtifact, classifier: String?, extension: String?): String {
    val newPath = artifact.getPathForExtraArtifact(classifier, extension)
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, newPath) + JarFileSystem.JAR_SEPARATOR
  }

  internal fun getSourceLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return MavenLanguageLevelFinder(mavenProject, true, false).getMavenLanguageLevel()
  }

  internal fun getSourceLanguageLevel(mavenProject: MavenProject, executionId: String): LanguageLevel? {
    return MavenLanguageLevelFinder(mavenProject, true, false, executionId).getMavenLanguageLevel()
  }

  internal fun getTestSourceLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return MavenLanguageLevelFinder(mavenProject, true, true).getMavenLanguageLevel()
  }

  internal fun getTargetLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return MavenLanguageLevelFinder(mavenProject, false, false).getMavenLanguageLevel()
  }

  internal fun getTargetLanguageLevel(mavenProject: MavenProject, executionId: String): LanguageLevel? {
    return MavenLanguageLevelFinder(mavenProject, false, false, executionId).getMavenLanguageLevel()
  }

  internal fun getTestTargetLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return MavenLanguageLevelFinder(mavenProject, false, true).getMavenLanguageLevel()
  }

  internal fun getLanguageLevel(mavenProject: MavenProject, supplier: Supplier<LanguageLevel?>): LanguageLevel {
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
    val plugin = project.findCompilerPlugin() ?: return false
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
    val plugin = project.findCompilerPlugin()
    if (plugin == null) return false
    val executions = plugin.executions
    if (executions == null || executions.isEmpty()) return false
    val compileExec = executions.find { isCompileExecution(it) }
    val testExec = executions.find { isTestCompileExecution(it) }
    if (compileExec == null) return testExec != null
    if (testExec == null) return true
    return !JDOMUtil.areElementsEqual(compileExec.configurationElement, testExec.configurationElement)
  }

  private fun isTestCompileExecution(e: MavenPlugin.Execution): Boolean {
    return checkExecution(e, PHASE_TEST_COMPILE, GOAL_TEST_COMPILE, EXECUTION_TEST_COMPILE)
  }

  private fun isCompileExecution(e: MavenPlugin.Execution): Boolean {
    return checkExecution(e, PHASE_COMPILE, GOAL_COMPILE, EXECUTION_COMPILE)
  }

  private fun checkExecution(e: MavenPlugin.Execution, phase: String, goal: String, defaultExecId: String): Boolean {
    return "none" != e.phase &&
           (phase == e.phase ||
            (e.goals != null && e.goals.contains(goal)) ||
            (defaultExecId == e.executionId)
           )
  }

  internal fun multiReleaseOutputSyncEnabled(): Boolean {
    return `is`("maven.sync.compileSourceRoots.and.multiReleaseOutput")
  }

  private fun compilerExecutions(project: MavenProject): List<MavenPlugin.Execution> {
    val plugin = project.findCompilerPlugin() ?: return emptyList()
    return plugin.executions ?: return emptyList()
  }

  internal fun getNonDefaultCompilerExecutions(project: MavenProject): List<String> {
    if (!multiReleaseOutputSyncEnabled()) return emptyList()
    return compilerExecutions(project)
      .filter { (it.phase == PHASE_COMPILE || it.phase == null) && it.configurationElement?.getChild("compileSourceRoots")?.children?.isNotEmpty() == true }
      .map { it.executionId }
      .filter { it != EXECUTION_COMPILE && it != EXECUTION_TEST_COMPILE }
  }

  internal fun getCompileSourceRoots(project: MavenProject, executionId: String): List<String> {
    return compilerExecutions(project)
      .firstOrNull { it.executionId == executionId }
      ?.configurationElement
      ?.getChild("compileSourceRoots")
      ?.children
      ?.mapNotNull { it.textTrim }
      .orEmpty()
  }

  internal fun escapeCompileSourceRootModuleSuffix(suffix: String): String {
    return compileSourceRootEncoder.encode(suffix)
  }

  internal fun unescapeCompileSourceRootModuleSuffix(suffix: String): String {
    return compileSourceRootEncoder.decode(suffix)
  }

  private class MavenLanguageLevelFinder(
    val mavenProject: MavenProject,
    val isSource: Boolean,
    val isTest: Boolean,
    val executionId: String? = null,
  ) {
    fun getMavenLanguageLevel(): LanguageLevel? {
      val useReleaseCompilerProp = isReleaseCompilerProp(mavenProject)
      val releaseLevel = if (useReleaseCompilerProp) getCompilerLevel("release") else null
      return releaseLevel ?: getCompilerLevel(if (isSource) "source" else "target")
    }

    private fun getConfigs(): List<Element> {
      if (null != executionId) return compilerExecutions(mavenProject)
        .filter { it.executionId == executionId }
        .mapNotNull { it.configurationElement }

      if (isTest) return mavenProject.testCompilerConfigs

      val nonDefaultExecutions = getNonDefaultCompilerExecutions(mavenProject).toSet()

      return mavenProject.compilerExecutions
               .filter { !nonDefaultExecutions.contains(it.executionId) }
               .mapNotNull { it.configurationElement } +
             mavenProject.pluginConfig
    }

    private fun getCompilerLevel(levelName: String): LanguageLevel? {
      if (isTest) {
        val testLevelName = "test${levelName.replaceFirstChar { it.titlecase() }}"
        val testLevel = doGetCompilerLevel(testLevelName)
        if (null != testLevel) return testLevel
      }
      return doGetCompilerLevel(levelName)
    }

    private fun doGetCompilerLevel(levelName: String): LanguageLevel? {
      val configs = getConfigs()
      val fallbackProperty = "maven.compiler.$levelName"
      val levels = configs.mapNotNull { LanguageLevel.parse(findChildValueByPath(it, levelName)) }
      val maxLevel = levels.maxWithOrNull(Comparator.naturalOrder())?.toJavaVersion()?.toFeatureString()
      val level = maxLevel ?: mavenProject.properties.getProperty(fallbackProperty)
      return LanguageLevel.parse(level)
    }
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

  internal fun getDefaultLevel(mavenProject: MavenProject): LanguageLevel {
    val plugin = mavenProject.findCompilerPlugin()
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

    val compilerConfiguration = mavenProject.getPluginConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)
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

  internal fun isCompilerTestSupport(mavenProject: MavenProject): Boolean {
    return StringUtil.compareVersionNumbers(MavenUtil.getCompilerPluginVersion(mavenProject), "2.1") >= 0
  }

  internal fun isMainOrTestModule(module: Module): Boolean {
    val type = module.getMavenModuleType()
    return type == StandardMavenModuleType.MAIN_ONLY || type == StandardMavenModuleType.TEST_ONLY
  }

  @JvmStatic
  fun findPomXml(module: Module): VirtualFile? {
    val project = module.project
    val storage = project.workspaceModel.currentSnapshot
    val pomPath = storage.resolve(ModuleId(module.name))?.exModuleOptions?.linkedProjectId?.toNioPathOrNull() ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(pomPath)
  }

  internal fun Module.getMavenModuleType(): StandardMavenModuleType {
    val default = StandardMavenModuleType.SINGLE_MODULE
    val moduleEntity = findModuleEntity() ?: return default
    return moduleEntity.getMavenModuleType()
  }

  private fun ModuleEntity.getMavenModuleType(): StandardMavenModuleType {
    val default = StandardMavenModuleType.SINGLE_MODULE
    val moduleTypeString = exModuleOptions?.externalSystemModuleType ?: return default
    return try {
      enumValueOf<StandardMavenModuleType>(moduleTypeString)
    }
    catch (_: IllegalArgumentException) {
      MavenLog.LOG.warn("Unknown module type: $moduleTypeString")
      default
    }
  }

  internal fun getModuleNames(project: Project, pomXml: VirtualFile): List<String> {
    val storage = project.workspaceModel.currentSnapshot
    val pomXmlPath = pomXml.toNioPath()
    return storage.entities<ModuleEntity>()
      .filter { it.exModuleOptions?.linkedProjectId?.toNioPathOrNull() == pomXmlPath }
      .map { it.name }
      .toList()
  }

  internal fun createPreviewModule(project: Project, contentRoot: VirtualFile): Module? {
    return WriteAction.compute<Module?, RuntimeException?>(ThrowableComputable {
      val modulePath = contentRoot.toNioPath().resolve(project.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION)
      val module = ModuleManager.getInstance(project).newModule(modulePath, ModuleTypeManager.getInstance().getDefaultModuleType().id)
      val modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel()
      modifiableModel.addContentEntry(contentRoot)
      modifiableModel.commit()

      ExternalSystemUtil.markModuleAsMaven(module, null, true)
      module
    })
  }

  internal fun MavenProject.getAllCompilerConfigs(): List<Element> {
    val result = ArrayList<Element>(1)
    this.getPluginConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)?.let(result::add)

    this.findCompilerPlugin()
      ?.executions?.filter { it.goals.contains("compile") }
      ?.filter { it.phase != "none" }
      ?.mapNotNull { it.configurationElement }
      ?.forEach(result::add)
    return result
  }

  internal val MavenProject.declaredAnnotationProcessors: List<String>
    get() {
      return compilerConfigsOrPluginConfig.flatMap { getDeclaredAnnotationProcessors(it) }
    }

  private val MavenProject.compilerConfigsOrPluginConfig: List<Element>
    get() {
      val configurations: List<Element> = compilerConfigs
      if (!configurations.isEmpty()) return configurations
      return pluginConfig
    }

  private val MavenProject.pluginConfig: List<Element>
    get() {
      val configuration: Element? = getPluginConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)
      return ContainerUtil.createMaybeSingletonList(configuration)
    }

  private val MavenProject.compilerExecutions: List<MavenPlugin.Execution>
    get() {
      val plugin = findCompilerPlugin()
      if (plugin == null) return emptyList()
      return plugin.getCompileExecutions()
    }

  private val MavenProject.compilerConfigs: List<Element>
    get() {
      return compilerExecutions.mapNotNull { it.configurationElement }
    }

  private val MavenProject.testCompilerConfigs: List<Element>
    get() {
      val plugin = findCompilerPlugin()
      if (plugin == null) return emptyList()
      return plugin.getTestCompileExecutionConfigurations()
    }

  private fun MavenPlugin.getCompileExecutions(): List<MavenPlugin.Execution> {
    return executions.filter { isCompileExecution(it) }
  }

  private fun MavenPlugin.getTestCompileExecutions(): List<MavenPlugin.Execution> {
    return executions.filter { isTestCompileExecution(it) }
  }

  internal fun MavenPlugin.getCompileExecutionConfigurations(): List<Element> {
    return getCompileExecutions().mapNotNull { it.configurationElement }
  }

  internal fun MavenPlugin.getTestCompileExecutionConfigurations(): List<Element> {
    return getTestCompileExecutions().mapNotNull { it.configurationElement }
  }

  private fun MavenProject.getDeclaredAnnotationProcessors(compilerConfig: Element): MutableList<String> {
    val result: MutableList<String> = ArrayList()
    if (procMode != ProcMode.NONE) {
      val processors: Element? = compilerConfig.getChild("annotationProcessors")
      if (processors != null) {
        for (element: Element in processors.getChildren("annotationProcessor")) {
          val processorClassName: String = element.textTrim
          if (!processorClassName.isEmpty()) {
            result.add(processorClassName)
          }
        }
      }
    }
    else {
      val bscMavenPlugin: MavenPlugin? = findPlugin("org.bsc.maven", "maven-processor-plugin")
      if (bscMavenPlugin != null) {
        var bscCfg: Element? = bscMavenPlugin.getGoalConfiguration("process")
        if (bscCfg == null) {
          bscCfg = bscMavenPlugin.configurationElement
        }

        if (bscCfg != null) {
          val bscProcessors: Element? = bscCfg.getChild("processors")
          if (bscProcessors != null) {
            for (element: Element in bscProcessors.getChildren("processor")) {
              val processorClassName: String = element.textTrim
              if (!processorClassName.isEmpty()) {
                result.add(processorClassName)
              }
            }
          }
        }
      }
    }
    return result
  }

  internal val MavenProject.annotationProcessorOptions: Map<String, String>
    get() {
      val compilerConfig: Element? = compilerConfig
      if (compilerConfig == null) {
        return emptyMap()
      }
      if (procMode != ProcMode.NONE) {
        return getAnnotationProcessorOptionsFromCompilerConfig(compilerConfig)
      }
      val bscMavenPlugin: MavenPlugin? = findPlugin("org.bsc.maven", "maven-processor-plugin")
      if (bscMavenPlugin != null) {
        return getAnnotationProcessorOptionsFromProcessorPlugin(bscMavenPlugin)
      }
      return emptyMap()
    }

  private fun getAnnotationProcessorOptionsFromCompilerConfig(compilerConfig: Element): Map<String, String> {
    val res: MutableMap<String, String> = LinkedHashMap()

    val compilerArgument: String? = compilerConfig.getChildText("compilerArgument")
    addAnnotationProcessorOptionFromParameterString(compilerArgument, res)

    val compilerArgs: Element? = compilerConfig.getChild("compilerArgs")
    if (compilerArgs != null) {
      for (e: Element in compilerArgs.children) {
        if (!StringUtil.equals(e.name, "arg")) continue
        val arg: String = e.textTrim
        addAnnotationProcessorOption(arg, res)
      }
    }

    val compilerArguments: Element? = compilerConfig.getChild("compilerArguments")
    if (compilerArguments != null) {
      for (e: Element in compilerArguments.children) {
        var name: String = e.name
        name = name.removePrefix("-")

        if (name.length > 1 && name[0] == 'A') {
          res[name.substring(1)] = e.textTrim
        }
      }
    }
    return res
  }

  private fun getAnnotationProcessorOptionsFromProcessorPlugin(bscMavenPlugin: MavenPlugin): Map<String, String> {
    var cfg: Element? = bscMavenPlugin.getGoalConfiguration("process")
    if (cfg == null) {
      cfg = bscMavenPlugin.configurationElement
    }
    val res: java.util.LinkedHashMap<String, String> = LinkedHashMap()
    if (cfg != null) {
      val compilerArguments = cfg.getChildText("compilerArguments")
      addAnnotationProcessorOptionFromParameterString(compilerArguments, res)

      val optionsElement: Element? = cfg.getChild("options")
      if (optionsElement != null) {
        for (option: Element in optionsElement.children) {
          res[option.name] = option.text
        }
      }
    }
    return res
  }

  private fun addAnnotationProcessorOptionFromParameterString(compilerArguments: String?, res: MutableMap<String, String>) {
    if (!compilerArguments.isNullOrBlank()) {
      val parametersList = ParametersList()
      parametersList.addParametersString(compilerArguments)

      for (param: String in parametersList.parameters) {
        addAnnotationProcessorOption(param, res)
      }
    }
  }

  private fun addAnnotationProcessorOption(compilerArg: String?, optionsMap: MutableMap<String, String>) {
    if (compilerArg == null || compilerArg.trim { it <= ' ' }.isEmpty()) return

    if (compilerArg.startsWith("-A")) {
      val idx: Int = compilerArg.indexOf('=', 3)
      if (idx >= 0) {
        optionsMap[compilerArg.substring(2, idx)] = compilerArg.substring(idx + 1)
      }
      else {
        optionsMap[compilerArg.substring(2)] = ""
      }
    }
  }

  internal val MavenProject.procMode: ProcMode
    get() {
      var compilerConfiguration: Element? = getPluginExecutionConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID,
                                                                            "default-compile")
      if (compilerConfiguration == null) {
        compilerConfiguration = compilerConfig
      }

      if (compilerConfiguration == null) {
        return ProcMode.BOTH
      }

      val procElement: Element? = compilerConfiguration.getChild("proc")
      if (procElement != null) {
        val procMode: String = procElement.value
        return if (("only".equals(procMode, ignoreCase = true))) ProcMode.ONLY
        else if (("none".equals(procMode, ignoreCase = true))) ProcMode.NONE else ProcMode.BOTH
      }

      val compilerArgument: String? = compilerConfiguration.getChildTextTrim("compilerArgument")
      if ("-proc:none" == compilerArgument) {
        return ProcMode.NONE
      }
      if ("-proc:only" == compilerArgument) {
        return ProcMode.ONLY
      }

      val compilerArguments: Element? = compilerConfiguration.getChild("compilerArgs")
      if (compilerArguments != null) {
        for (element: Element in compilerArguments.children) {
          val arg: String = element.value
          if ("-proc:none" == arg) {
            return ProcMode.NONE
          }
          if ("-proc:only" == arg) {
            return ProcMode.ONLY
          }
        }
      }

      return ProcMode.BOTH
    }

  private fun MavenProject.getPluginExecutionConfiguration(groupId: String?, artifactId: String?, executionId: String): Element? {
    val plugin: MavenPlugin? = findPlugin(groupId, artifactId)
    if (plugin == null) return null
    return plugin.getExecutionConfiguration(executionId)
  }

  internal fun MavenProject.getAnnotationProcessorDirectory(testSources: Boolean): @NlsSafe String {
    if (procMode == ProcMode.NONE) {
      val bscMavenPlugin: MavenPlugin? = findPlugin("org.bsc.maven", "maven-processor-plugin")
      val cfg: Element? = getPluginGoalConfiguration(bscMavenPlugin, if (testSources) "process-test" else "process")
      if (bscMavenPlugin != null && cfg == null) {
        return buildDirectory + (if (testSources) "/generated-sources/apt-test" else "/generated-sources/apt")
      }
      if (cfg != null) {
        var out: String? = findChildValueByPath(cfg, "outputDirectory")
        if (out == null) {
          out = findChildValueByPath(cfg, "defaultOutputDirectory")
          if (out == null) {
            return buildDirectory + (if (testSources) "/generated-sources/apt-test" else "/generated-sources/apt")
          }
        }

        if (!File(out).isAbsolute) {
          out = "$directory/$out"
        }

        return out
      }
    }

    val def: String = getGeneratedSourcesDirectory(testSources) + (if (testSources) "/test-annotations" else "/annotations")
    return findChildValueByPath(
      compilerConfig, if (testSources) "generatedTestSourcesDirectory" else "generatedSourcesDirectory", def)!!
  }

  private val MavenProject.compilerConfig: Element?
    get() {
      val executionConfiguration: Element? =
        getPluginExecutionConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID, "default-compile")
      if (executionConfiguration != null) return executionConfiguration
      return getPluginConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)
    }

  private fun MavenProject.findCompilerPlugin(): MavenPlugin? {
    return findPlugin(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)
  }
}
