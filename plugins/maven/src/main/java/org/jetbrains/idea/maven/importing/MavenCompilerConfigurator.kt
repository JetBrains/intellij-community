// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.containers.ContainerUtil.addIfNotNull
import com.intellij.util.text.nullize
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.MavenDisposable
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
class MavenCompilerConfigurator : MavenImporter("org.apache.maven.plugins", "maven-compiler-plugin"),
                                  MavenWorkspaceConfigurator {
  private val LOG = Logger.getInstance(MavenCompilerConfigurator::class.java)


  override fun isApplicable(mavenProject: MavenProject?): Boolean {
    return true
  }

  override fun processChangedModulesOnly(): Boolean {
    return false
  }

  @Throws(MavenProcessCanceledException::class)
  override fun resolve(project: Project,
                       mavenProject: MavenProject,
                       nativeMavenProject: NativeMavenProjectHolder,
                       embedder: MavenEmbedderWrapper,
                       context: ResolveContext) {
    if (!super.isApplicable(mavenProject)) return
    if (!Registry.`is`("maven.import.compiler.arguments", true) ||  !MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler) return

    val defaultCompilerExtension = MavenCompilerExtension.EP_NAME.extensions.find {
      it.resolveDefaultCompiler(project, mavenProject, nativeMavenProject, embedder, context)
    }
    if (project.getUserData(DEFAULT_COMPILER_EXTENSION) == null) {
      project.putUserData(DEFAULT_COMPILER_EXTENSION, defaultCompilerExtension)
    }
  }

  override fun isMigratedToConfigurator(): Boolean {
    return true
  }

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

    MavenProjectImporterBase.removeOutdatedCompilerConfigSettings(context.project)
  }

  override fun preProcess(module: Module,
                          mavenProject: MavenProject,
                          changes: MavenProjectChanges,
                          modifiableModelsProvider: IdeModifiableModelsProvider) {

    // #resolve() puts the extension in the project's user data
    // we need to clean it (for the next import), so we transfer it to the current IdeModifiableModelsProvider
    val defaultCompilerExtension = module.project.getUserData(DEFAULT_COMPILER_EXTENSION)
    if (defaultCompilerExtension != null) {
      modifiableModelsProvider.putUserData(DEFAULT_COMPILER_EXTENSION, defaultCompilerExtension)
      module.project.putUserData(DEFAULT_COMPILER_EXTENSION, null)
    }

    val config = getCompilerConfigurationWhenApplicable(module.project, mavenProject) ?: return

    var compilers = modifiableModelsProvider.getUserData(ALL_PROJECTS_COMPILERS)
    if (compilers == null) {
      compilers = mutableSetOf()
      modifiableModelsProvider.putUserData(ALL_PROJECTS_COMPILERS, compilers)
    }
    compilers.add(getCompilerId(config))
  }

  private fun getCompilerConfigurationWhenApplicable(project: Project, mavenProject: MavenProject): Element? {
    if (!Registry.`is`("maven.import.compiler.arguments", true) ||
        !MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler) return null
    if (!super.isApplicable(mavenProject)) return null
    return getConfig(mavenProject)
  }

  override fun process(modifiableModelsProvider: IdeModifiableModelsProvider,
                       module: Module,
                       rootModel: MavenRootModelAdapter?,
                       mavenModel: MavenProjectsTree,
                       mavenProject: MavenProject,
                       changes: MavenProjectChanges,
                       mavenProjectToModuleName: Map<MavenProject, String>,
                       postTasks: List<MavenProjectsProcessorTask>) {
    val project = module.project

    // select (and cache) default compiler extension
    var defaultCompilerExtension = modifiableModelsProvider.getUserData(DEFAULT_COMPILER_EXTENSION)
    if (defaultCompilerExtension == null) {
      defaultCompilerExtension = selectDefaultCompilerExtension(
        modifiableModelsProvider.getUserData(ALL_PROJECTS_COMPILERS).orEmpty())
      modifiableModelsProvider.putUserData(DEFAULT_COMPILER_EXTENSION, defaultCompilerExtension)
    }

    val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl

    // configure (once) project settings
    if (modifiableModelsProvider.getUserData(DEFAULT_COMPILER_IS_SET) == null) {
      setDefaultProjectCompiler(project, ideCompilerConfiguration, defaultCompilerExtension)
      modifiableModelsProvider.putUserData(DEFAULT_COMPILER_IS_SET, true)
    }

    // configure module settings
    configureModules(module.project,
                     sequenceOf(MavenProjectWithModulesData(mavenProject, listOf(module))),
                     ideCompilerConfiguration,
                     defaultCompilerExtension)
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
      modules.forEach { module ->
        applyCompilerExtensionConfiguration(mavenProject, module, ideCompilerConfiguration, defaultCompilerExtension)
        configureTargetLevel(mavenProject, module, ideCompilerConfiguration, defaultCompilerExtension)
      }

      excludeArchetypeResources(project, mavenProject, ideCompilerConfiguration)
    }
  }

  private fun applyCompilerExtensionConfiguration(mavenProject: MavenProject,
                                                  module: Module,
                                                  ideCompilerConfiguration: CompilerConfigurationImpl,
                                                  defaultCompilerExtension: MavenCompilerExtension?) {
    val mavenConfiguration = MavenCompilerConfiguration(mavenProject.properties[MAVEN_COMPILER_PARAMETERS]?.toString(),
                                                        getConfig(mavenProject))
    val projectCompilerId = if (mavenProject.packaging != "pom") mavenConfiguration.pluginConfiguration?.let { getCompilerId(it) } else null

    for (compilerExtension in MavenCompilerExtension.EP_NAME.extensions) {
      val applyThisExtension =
        projectCompilerId == compilerExtension.mavenCompilerId
        || projectCompilerId == null && compilerExtension == defaultCompilerExtension

      val compilerOptions = compilerExtension.getCompiler(module.project)?.options
      if (applyThisExtension && !mavenConfiguration.isEmpty()) {
        compilerExtension.configureOptions(compilerOptions, module, mavenProject, collectCompilerArgs(mavenConfiguration))
      }
      else {
        // cleanup obsolete options
        (compilerOptions as? JpsJavaCompilerOptions)?.let {
          ideCompilerConfiguration.setAdditionalOptions(it, module, emptyList())
        }
      }
    }
  }

  private fun configureTargetLevel(mavenProject: MavenProject,
                                   module: Module,
                                   ideCompilerConfiguration: CompilerConfiguration,
                                   defaultCompilerExtension: MavenCompilerExtension?) {
    var targetLevel = defaultCompilerExtension?.getDefaultCompilerTargetLevel(mavenProject, module)
    if (targetLevel == null) {
      var level: LanguageLevel?
      if (MavenImportUtil.isTestModule(module.name)) {
        level = MavenImportUtil.getTargetTestLanguageLevel(mavenProject)
        if (level == null) {
          level = MavenImportUtil.getTargetLanguageLevel(mavenProject)
        }
      }
      else {
        level = MavenImportUtil.getTargetLanguageLevel(mavenProject)
      }
      if (level == null) {
        level = MavenImportUtil.getDefaultLevel(mavenProject)
      }
      // default source and target settings of maven-compiler-plugin is 1.5, see details at http://maven.apache.org/plugins/maven-compiler-plugin!
      targetLevel = level.toJavaVersion().toString()
    }
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

  companion object {
    private val ALL_PROJECTS_COMPILERS = Key.create<MutableSet<String>>("maven.compilers")
    private val DEFAULT_COMPILER_EXTENSION = Key.create<MavenCompilerExtension>("default.compiler")
    private val DEFAULT_COMPILER_IS_SET = Key.create<Boolean>("default.compiler.updated")
    private const val JAVAC_ID = "javac"
    private const val MAVEN_COMPILER_PARAMETERS = "maven.compiler.parameters"

    private fun getCompilerId(config: Element): String {
      val compilerId = config.getChildTextTrim("compilerId")
      if (compilerId.isNullOrBlank() || JAVAC_ID == compilerId || hasUnresolvedProperty(compilerId)) return JAVAC_ID
      else return compilerId
    }

    private const val propStartTag = "\${"
    private const val propEndTag = "}"

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

    private fun collectCompilerArgs(mavenCompilerConfiguration: MavenCompilerConfiguration): List<String> {
      val options = mutableListOf<String>()

      val pluginConfiguration = mavenCompilerConfiguration.pluginConfiguration
      val parameters = pluginConfiguration?.getChild("parameters")

      if (parameters?.textTrim?.toBoolean() == true) {
        options += "-parameters"
      }
      else if (parameters == null && mavenCompilerConfiguration.propertyCompilerParameters?.toBoolean() == true) {
        options += "-parameters"
      }

      if (pluginConfiguration == null) return options

      val compilerArguments = pluginConfiguration.getChild("compilerArguments")
      if (compilerArguments != null) {
        val unresolvedArgs = mutableSetOf<String>()
        val effectiveArguments = compilerArguments.children.map {
          val key = it.name.run { if (startsWith("-")) this else "-$this" }
          val value = getResolvedText(it)
          if (value == null && hasUnresolvedProperty(it.textTrim)) {
            unresolvedArgs += key
          }
          key to value
        }.toMap()

        effectiveArguments.forEach { key, value ->
          if (key.startsWith("-A") && value != null) {
            options.add("$key=$value")
          }
          else if (key !in unresolvedArgs) {
            options.add(key)
            addIfNotNull(options, value)
          }
        }
      }

      addIfNotNull(options, getResolvedText(pluginConfiguration.getChildTextTrim("compilerArgument")))

      val compilerArgs = pluginConfiguration.getChild("compilerArgs")
      if (compilerArgs != null) {
        for (arg in compilerArgs.getChildren("arg")) {
          addIfNotNull(options, getResolvedText(arg))
        }
        for (compilerArg in compilerArgs.getChildren("compilerArg")) {
          addIfNotNull(options, getResolvedText(compilerArg))
        }
      }
      return options
    }
  }
}

private data class MavenCompilerConfiguration(val propertyCompilerParameters: String?, val pluginConfiguration: Element?) {
  fun isEmpty(): Boolean = propertyCompilerParameters == null && pluginConfiguration == null
}
