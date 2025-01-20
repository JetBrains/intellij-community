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
  private const val GOAL_TEST_COMPILE = "test-compile"

  private const val EXECUTION_COMPILE = "default-compile"
  private const val EXECUTION_TEST_COMPILE = "default-testCompile"

  internal fun getArtifactUrlForClassifierAndExtension(artifact: MavenArtifact, classifier: String?, extension: String?): String {
    val newPath = artifact.getPathForExtraArtifact(classifier, extension)
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, newPath) + JarFileSystem.JAR_SEPARATOR
  }

  internal fun getSourceLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, true, false)
  }

  internal fun getTestSourceLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, true, true)
  }

  internal fun getTargetLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, false, false)
  }

  internal fun getTestTargetLanguageLevel(mavenProject: MavenProject): LanguageLevel? {
    return getMavenLanguageLevel(mavenProject, false, true)
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
    val testExec = executions.find { isTestExecution(it) }
    if (compileExec == null) return testExec != null
    if (testExec == null) return true
    return !JDOMUtil.areElementsEqual(compileExec.configurationElement, testExec.configurationElement)
  }

  private fun isTestExecution(e: MavenPlugin.Execution): Boolean {
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

  private fun getMavenLanguageLevel(mavenProject: MavenProject, isSource: Boolean, isTest: Boolean): LanguageLevel? {
    val useReleaseCompilerProp = isReleaseCompilerProp(mavenProject)
    val releaseLevel = if (useReleaseCompilerProp) getMavenLanguageLevelFromRelease(mavenProject, isTest) else null
    return releaseLevel ?: getMavenLanguageLevelFromSourceOrTarget(mavenProject, isSource, isTest)
  }

  private fun getMavenLanguageLevelFromRelease(mavenProject: MavenProject, isTest: Boolean): LanguageLevel? {
    val mavenProjectReleaseLevel = if (isTest) mavenProject.testReleaseLevel else mavenProject.releaseLevel
    return LanguageLevel.parse(mavenProjectReleaseLevel)
  }

  private fun getMavenLanguageLevelFromSourceOrTarget(project: MavenProject, isSource: Boolean, isTest: Boolean): LanguageLevel? {
    val mavenProjectLanguageLevel = if (isTest) {
      if (isSource) project.testSourceLevel else project.testTargetLevel
    }
    else {
      if (isSource) project.sourceLevel else project.targetLevel
    }
    var level = LanguageLevel.parse(mavenProjectLanguageLevel)
    if (level == null && StringUtil.isNotEmpty(mavenProjectLanguageLevel)) {
      level = LanguageLevel.HIGHEST
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

  internal fun isMainOrTestModule(project: Project, moduleName: String): Boolean {
    val type = getMavenModuleType(project, moduleName)
    return type == StandardMavenModuleType.MAIN_ONLY || type == StandardMavenModuleType.TEST_ONLY
  }

  internal fun isTestModule(project: Project, moduleName: String): Boolean {
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

  internal fun getMavenModuleType(project: Project, moduleName: @NlsSafe String): StandardMavenModuleType {
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
      return compilerConfigs.flatMap { getDeclaredAnnotationProcessors(it) }
    }

  private val MavenProject.sourceLevel: String?
    get() {
      return getCompilerLevel(false, "source")
    }

  private val MavenProject.targetLevel: String?
    get() {
      return getCompilerLevel(false, "target")
    }

  private val MavenProject.releaseLevel: String?
    get() {
      return getCompilerLevel(false, "release")
    }

  private val MavenProject.testSourceLevel: String?
    get() {
      return getCompilerLevel(true, "source")
    }

  private val MavenProject.testTargetLevel: String?
    get() {
      return getCompilerLevel(true, "target")
    }

  private val MavenProject.testReleaseLevel: String?
    get() {
      return getCompilerLevel(true, "release")
    }

  private fun MavenProject.getCompilerLevel(forTests: Boolean, level: String): String? {
    val configs = if (forTests) testCompilerConfigs else compilerConfigs
    val finalLevel = if (forTests) "test${level.replaceFirstChar { it.titlecase() }}" else level
    val fallbackProperty = "maven.compiler.$finalLevel"
    val levels = configs.mapNotNull { LanguageLevel.parse(findChildValueByPath(it, finalLevel)) }
    val maxLevel = levels.maxWithOrNull(Comparator.naturalOrder())?.toJavaVersion()?.toFeatureString()
    return maxLevel ?: properties.getProperty(fallbackProperty)
  }

  private val MavenProject.compilerConfigs: List<Element>
    get() {
      val configurations: List<Element> = compileExecutionConfigurations
      if (!configurations.isEmpty()) return configurations
      val configuration: Element? = getPluginConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)
      return ContainerUtil.createMaybeSingletonList(configuration)
    }

  private val MavenProject.compileExecutionConfigurations: List<Element>
    get() {
      val plugin: MavenPlugin? = findCompilerPlugin()
      if (plugin == null) return emptyList()
      return plugin.compileExecutionConfigurations
    }

  private val MavenProject.testCompilerConfigs: List<Element>
    get() {
      val plugin: MavenPlugin? = findCompilerPlugin()
      if (plugin == null) return emptyList()
      return plugin.testCompileExecutionConfigurations
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

  private fun MavenProject.getAnnotationProcessorOptionsFromCompilerConfig(compilerConfig: Element): Map<String, String> {
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

  private fun MavenProject.getAnnotationProcessorOptionsFromProcessorPlugin(bscMavenPlugin: MavenPlugin): Map<String, String> {
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

  private fun MavenProject.addAnnotationProcessorOptionFromParameterString(compilerArguments: String?, res: MutableMap<String, String>) {
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
