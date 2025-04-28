// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.PathUtil
import com.intellij.util.text.nullize
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.MavenDisposable
import org.jetbrains.idea.maven.importing.MavenImportUtil.getCompileExecutionConfigurations
import org.jetbrains.idea.maven.importing.MavenImportUtil.getMavenModuleType
import org.jetbrains.idea.maven.importing.MavenImportUtil.getTestCompileExecutionConfigurations
import org.jetbrains.idea.maven.importing.MavenImportUtil.unescapeCompileSourceRootModuleSuffix
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import java.nio.file.Path

internal val DEFAULT_COMPILER_EXTENSION = Key.create<MavenCompilerExtension>("default.compiler")
private const val JAVAC_ID = "javac"
private const val MAVEN_COMPILER_PARAMETERS = "maven.compiler.parameters"

private const val propStartTag = "\${"
private const val propEndTag = "}"

private val LOG = Logger.getInstance(MavenCompilerConfigurator::class.java)

private const val ARTIFACT_ID = "maven-compiler-plugin"
private const val GROUP_ID = "org.apache.maven.plugins"

@ApiStatus.Internal
class MavenCompilerConfigurator : MavenApplicableConfigurator(GROUP_ID, ARTIFACT_ID),
                                  MavenWorkspaceConfigurator {
  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    var defaultCompilerExtension = context.project.getUserData(DEFAULT_COMPILER_EXTENSION)
    context.putUserData(DEFAULT_COMPILER_EXTENSION, null)

    if (defaultCompilerExtension == null) {
      val allCompilers = context.mavenProjectsWithModules.mapNotNullTo(mutableSetOf()) {
        getCompilerConfigurationWhenApplicable(context.project, it.mavenProject)?.let { config -> getCompilerId(config) }
      }
      defaultCompilerExtension = selectDefaultCompilerExtension(allCompilers)
    }

    context.putUserData(DEFAULT_COMPILER_EXTENSION, defaultCompilerExtension)
  }

  override fun afterModelApplied(context: MavenWorkspaceConfigurator.AppliedModelContext) {
    val defaultCompilerExtension = context.getUserData(DEFAULT_COMPILER_EXTENSION)

    val ideCompilerConfiguration = CompilerConfiguration.getInstance(context.project) as CompilerConfigurationImpl
    setDefaultProjectCompiler(context.project, ideCompilerConfiguration, defaultCompilerExtension)

    val data = context.mavenProjectsWithModules.map {
      MavenProjectWithModulesData(it.mavenProject, it.modules.map { it.module })
    }
    configureModules(context.project, data, ideCompilerConfiguration, defaultCompilerExtension)

    removeOutdatedCompilerConfigSettings(context.project)
  }

  private fun removeOutdatedCompilerConfigSettings(project: Project) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val javacOptions = JavacConfiguration.getOptions(project, JavacConfiguration::class.java)
    var options = javacOptions.ADDITIONAL_OPTIONS_STRING
    options = options.replaceFirst("(-target (\\S+))".toRegex(), "") // Old IDEAs saved
    javacOptions.ADDITIONAL_OPTIONS_STRING = options
  }

  private fun getCompilerConfigurationWhenApplicable(project: Project, mavenProject: MavenProject): Element? {
    if (!Registry.`is`("maven.import.compiler.arguments", true) ||
        !MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler) return null
    if (!super.isApplicable(mavenProject)) return null
    return getConfig(mavenProject)
  }

  private fun selectDefaultCompilerExtension(allCompilers: Set<String>): MavenCompilerExtension? {
    val defaultCompilerId = allCompilers.singleOrNull() ?: JAVAC_ID
    return MavenCompilerExtension.EP_NAME.extensions.find {
      defaultCompilerId == it.mavenCompilerId
    }
  }

  private fun setDefaultProjectCompiler(project: Project,
                                        ideCompilerConfiguration: CompilerConfigurationImpl,
                                        defaultCompilerExtension: MavenCompilerExtension?) {
    val backendCompiler = defaultCompilerExtension?.getCompiler(project) ?: return

    val autoDetectCompiler = MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler
    MavenLog.LOG.debug("maven compiler autodetect = ", autoDetectCompiler)

    if (ideCompilerConfiguration.defaultCompiler != backendCompiler && autoDetectCompiler) {
      if (ideCompilerConfiguration.registeredJavaCompilers.contains(backendCompiler)) {
        ideCompilerConfiguration.defaultCompiler = backendCompiler
      }
      else {
        LOG.error("$backendCompiler is not registered.")
      }
    }
  }

  private fun configureModules(project: Project,
                               mavenProjectWithModule: Sequence<MavenProjectWithModulesData>,
                               ideCompilerConfiguration: CompilerConfigurationImpl,
                               defaultCompilerExtension: MavenCompilerExtension?) {
    mavenProjectWithModule.forEach { (mavenProject, modules) ->
      val compoundModule = modules.firstOrNull { it.getMavenModuleType() == StandardMavenModuleType.COMPOUND_MODULE }
      modules.forEach { module ->
        applyCompilerExtensionConfiguration(mavenProject, module, ideCompilerConfiguration, defaultCompilerExtension)
        configureTargetLevel(mavenProject, module, compoundModule, ideCompilerConfiguration, defaultCompilerExtension)
      }

      excludeArchetypeResources(project, mavenProject, ideCompilerConfiguration)
    }
  }

  private fun applyCompilerExtensionConfiguration(mavenProject: MavenProject,
                                                  module: Module,
                                                  ideCompilerConfiguration: CompilerConfigurationImpl,
                                                  defaultCompilerExtension: MavenCompilerExtension?) {
    val isTestModule = isTestModule(module)
    val mavenConfiguration = collectRawMavenData(module, mavenProject, isTestModule)
    val projectCompilerId = if (mavenProject.packaging == "pom") {
      null
    }
    else {
      mavenConfiguration.effectiveConfiguration?.let { getCompilerId(it) }
    }


    for (compilerExtension in MavenCompilerExtension.EP_NAME.extensions) {
      val applyThisExtension =
        projectCompilerId == compilerExtension.mavenCompilerId
        || projectCompilerId == null && compilerExtension == defaultCompilerExtension

      val compilerOptions = compilerExtension.getCompiler(module.project)?.options
      if (applyThisExtension && !mavenConfiguration.isEmpty()) {
        compilerExtension.configureOptions(compilerOptions, module, mavenProject, collectCompilerArgs(module, mavenProject, mavenConfiguration))
      }
      else {
        // cleanup obsolete options
        (compilerOptions as? JpsJavaCompilerOptions)?.let {
          ideCompilerConfiguration.setAdditionalOptions(it, module, emptyList())
        }
      }
    }
  }

  private fun collectRawMavenData(module: Module, mavenProject: MavenProject, forTests: Boolean): MavenCompilerConfigurationRawData {
    val pluginConfig = getConfig(mavenProject)
    val propertyConfig = mavenProject.properties[MAVEN_COMPILER_PARAMETERS]

    val executionConfig =
      if (forTests) {
        mavenProject.findPlugin(GROUP_ID, ARTIFACT_ID)?.getTestCompileExecutionConfigurations()
      }
      else {
        mavenProject.findPlugin(GROUP_ID, ARTIFACT_ID)?.getCompileExecutionConfigurations()
      }.findApplicable(module, mavenProject)
    return MavenCompilerConfigurationRawData(forTests, propertyConfig?.toString(), pluginConfig, executionConfig)
  }

  private fun configureTargetLevel(mavenProject: MavenProject,
                                   module: Module,
                                   compoundModule: Module?,
                                   ideCompilerConfiguration: CompilerConfiguration,
                                   defaultCompilerExtension: MavenCompilerExtension?) {
    var targetLevel = defaultCompilerExtension?.getDefaultCompilerTargetLevel(mavenProject, module)
    val moduleName = module.name
    MavenLog.LOG.debug("Bytecode target level $targetLevel in module $moduleName, compiler extension = ${defaultCompilerExtension?.mavenCompilerId}")
    if (targetLevel == null) {
      var level: LanguageLevel? = null
      val type = module.getMavenModuleType()
      if (type == StandardMavenModuleType.TEST_ONLY) {
        level = MavenImportUtil.getTestTargetLanguageLevel(mavenProject)
      }
      else if (type == StandardMavenModuleType.MAIN_ONLY_ADDITIONAL) {
        if (null != compoundModule) {
          val moduleSuffix = moduleName.substring(compoundModule.name.length + 1)
          val executionId = unescapeCompileSourceRootModuleSuffix(moduleSuffix)
          level = MavenImportUtil.getTargetLanguageLevel(mavenProject, executionId)
        }
      }
      else {
        level = MavenImportUtil.getTargetLanguageLevel(mavenProject)
      }
      if (level == null) {
        level = MavenImportUtil.getDefaultLevel(mavenProject)
      }
      level = MavenImportUtil.adjustLevelAndNotify(module.project, level)
      // default source and target settings of maven-compiler-plugin is 1.5, see details at http://maven.apache.org/plugins/maven-compiler-plugin!
      targetLevel = level.toJavaVersion().toString()
    }

    MavenLog.LOG.debug("Setting bytecode target level $targetLevel in module $moduleName")
    ideCompilerConfiguration.setBytecodeTargetLevel(module, targetLevel)
  }

  private fun excludeArchetypeResources(project: Project,
                                        mavenProject: MavenProject,
                                        ideCompilerConfiguration: CompilerConfiguration) {
    // Exclude src/main/archetype-resources
    val dir = runCatching {
      // EA-719125 Accessing invalid virtual file
      VfsUtil.findRelativeFile(mavenProject.directoryFile, "src", "main", "resources", "archetype-resources")
    }.getOrNull()
    if (dir != null && !ideCompilerConfiguration.isExcludedFromCompilation(dir)) {
      val cfg = ideCompilerConfiguration.excludedEntriesConfiguration
      cfg.addExcludeEntryDescription(ExcludeEntryDescription(dir, true, false, MavenDisposable.getInstance(project)))
    }
  }

  private data class MavenProjectWithModulesData(val mavenProject: MavenProject,
                                                 val modules: List<Module>)

  private fun getCompilerId(config: Element): String {
    val compilerId = config.getChildTextTrim("compilerId")
    if (compilerId.isNullOrBlank() || JAVAC_ID == compilerId || hasUnresolvedProperty(compilerId)) return JAVAC_ID
    else return compilerId
  }

  private fun hasUnresolvedProperty(txt: String): Boolean {
    val i = txt.indexOf(propStartTag)
    return i >= 0 && findClosingBraceOrNextUnresolvedProperty(i + 1, txt) != -1
  }

  private fun findClosingBraceOrNextUnresolvedProperty(index: Int, s: String): Int {
    if (index == -1) return -1
    val pair = s.findAnyOf(listOf(propEndTag, propStartTag), index) ?: return -1
    if (pair.second == propEndTag) return pair.first
    val nextIndex = if (pair.second == propStartTag) pair.first + 2 else pair.first + 1
    return findClosingBraceOrNextUnresolvedProperty(nextIndex, s)
  }

  private fun getResolvedText(txt: String?): String? {
    val result = txt.nullize() ?: return null
    if (hasUnresolvedProperty(result)) return null
    return result
  }

  private fun getResolvedText(it: Element): String? {
    return getResolvedText(it.textTrim)
  }

  private fun collectCompilerArgs(module: Module, mavenProject: MavenProject, configData: MavenCompilerConfigurationRawData): List<String> {
    val result = mutableListOf<String>()
    configData.propertyCompilerParameters?.let {
      if (it.toBoolean()) {
        result.add("-parameters")
      }
    }

    configData.effectiveConfiguration?.let {
      it.getChild("parameters")?.let {
        if (it.textTrim.toBoolean()) {
          result.add("-parameters")
        }
        else {
          result.remove("-parameters")
        }
      }

      if (configData.forTests) {
        val testData = collectTestCompilerArgs(it)
        if (testData.isNotEmpty()) {
          result.addAll(testData)
          return result.toList()
        }
      }
      result.addAll(collectCompilerArgs(it))
    }

    return result.toList()
  }


  private fun collectCompilerArgs(element: Element): List<String> {
    val result = ArrayList<String>()
    element.getChild("compilerArguments")?.let { arguments ->
      val unresolvedArgs = mutableSetOf<String>()
      val effectiveArguments = arguments.children.associate {
        val key = it.name.run { if (startsWith("-")) this else "-$this" }
        val value = getResolvedText(it)
        if (value == null && hasUnresolvedProperty(it.textTrim)) {
          unresolvedArgs += key
        }
        key to value
      }
      effectiveArguments.forEach { key, value ->
        if (key.startsWith("-A") && value != null) {
          result.add("$key=$value")
        }
        else if (key !in unresolvedArgs) {
          result.add(key)
          value?.let { result.add(it) }
        }
      }
    }
    element.getChild("compilerArgs")?.children?.forEach {
      val text = getResolvedText(it.text)
      if (text != null && !hasUnresolvedProperty(text)) {
        result.add(text)
      }
    }

    MavenJDOMUtil.findChildrenValuesByPath(element, "compilerArgs", "arg").forEach {

    }
    element.getChild("compilerArgument")?.textTrim?.let {
      val text = getResolvedText(it)
      if (text != null && !hasUnresolvedProperty(text)) {
        result.add(text)
      }
    }
    return result
  }

  private fun collectTestCompilerArgs(element: Element): List<String> {
    val result = ArrayList<String>()
    element.getChild("testCompilerArguments")?.let { arguments ->
      val unresolvedArgs = mutableSetOf<String>()
      val effectiveArguments = arguments.children.associate {
        val key = it.name.run { if (startsWith("-")) this else "-$this" }
        val value = getResolvedText(it)
        if (value == null && hasUnresolvedProperty(it.textTrim)) {
          unresolvedArgs += key
        }
        key to value
      }
      effectiveArguments.forEach { key, value ->
        if (key.startsWith("-A") && value != null) {
          result.add("$key=$value")
        }
        else if (key !in unresolvedArgs) {
          result.add(key)
          value?.let { result.add(it) }
        }
      }
    }
    element.getChild("testCompilerArgument")?.textTrim?.let {
      val text = getResolvedText(it)
      if (text != null && !hasUnresolvedProperty(text)) {
        result.add(text)
      }
    }
    return result
  }

  private fun isTestModule(module: Module): Boolean {
    val type = module.getMavenModuleType()
    return type == StandardMavenModuleType.TEST_ONLY
  }
}

private fun getSourceRoots(module: Module): List<Path> {
  return ModuleRootManager.getInstance(module).sourceRoots
    .map { it.toNioPath() }
}

private fun List<Element>?.findApplicable(module: Module, mavenProject: MavenProject): Element? {
  if (this == null || isEmpty()) return null
  if (size == 1) {
    return firstOrNull()
  }
  else {
    val type = module.getMavenModuleType()
    return when (type) {
      StandardMavenModuleType.AGGREGATOR -> firstOrNull()
      StandardMavenModuleType.SINGLE_MODULE -> firstOrNull()
      StandardMavenModuleType.COMPOUND_MODULE -> firstOrNull()
      StandardMavenModuleType.MAIN_ONLY -> this.firstOrNull { it.getChild("compileSourceRoots") == null }
      StandardMavenModuleType.MAIN_ONLY_ADDITIONAL -> firstOrNull { el ->
        val sourceRoots = getSourceRoots(module)
        MavenJDOMUtil.findChildrenValuesByPath(el, "compileSourceRoots", "compileSourceRoot")
          .map { Path.of(it) }
          .any { it in sourceRoots }
      }
      StandardMavenModuleType.TEST_ONLY -> firstOrNull()
    }
  }
}


private data class MavenCompilerConfigurationRawData(
  val forTests: Boolean,
  val propertyCompilerParameters: String?,
  val pluginConfiguration: Element?,
  val goalConfiguration: Element?,
) {
  fun isEmpty(): Boolean = propertyCompilerParameters == null
                           && pluginConfiguration == null && goalConfiguration == null


  val effectiveConfiguration
    get() = goalConfiguration ?: pluginConfiguration
}
