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
class MavenCompilerImporter : MavenImporter("org.apache.maven.plugins", "maven-compiler-plugin") {
  private val LOG = Logger.getInstance(MavenCompilerImporter::class.java)


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
    if (!Registry.`is`("maven.import.compiler.arguments", true)) return
    val compilerExtension = MavenCompilerExtension.EP_NAME.extensions.find {
      it.resolveDefaultCompiler(project, mavenProject, nativeMavenProject, embedder, context)
    }

    val autoDetectCompiler = MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler
    MavenLog.LOG.debug("maven compiler autodetect = ", autoDetectCompiler)

    val backendCompiler = compilerExtension?.getCompiler(project) ?: return
    val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    if (ideCompilerConfiguration.defaultCompiler != backendCompiler && autoDetectCompiler) {
      if (ideCompilerConfiguration.registeredJavaCompilers.contains(backendCompiler)) {
        ideCompilerConfiguration.defaultCompiler = backendCompiler
        project.putUserData(DEFAULT_COMPILER_IS_RESOLVED, true)
      }
      else {
        LOG.error(backendCompiler.toString() + " is not registered.")
      }
    }
    else {
      project.putUserData(DEFAULT_COMPILER_IS_RESOLVED, true)
    }


  }

  override fun preProcess(module: Module,
                          mavenProject: MavenProject,
                          changes: MavenProjectChanges,
                          modifiableModelsProvider: IdeModifiableModelsProvider) {
    if (!super.isApplicable(mavenProject)) return
    if (!Registry.`is`("maven.import.compiler.arguments", true)) return
    val config = getConfig(mavenProject) ?: return

    var compilers = modifiableModelsProvider.getUserData(COMPILERS)
    if (compilers == null) {
      compilers = mutableSetOf()
      modifiableModelsProvider.putUserData(COMPILERS, compilers)
    }
    compilers.add(getCompilerId(config))
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
    val compilers = modifiableModelsProvider.getUserData(COMPILERS)
    val defaultCompilerId = if (compilers != null && compilers.size == 1) compilers.first() else JAVAC_ID

    val mavenConfiguration: Lazy<MavenCompilerConfiguration> = lazy {
      MavenCompilerConfiguration(mavenProject.properties[MAVEN_COMPILER_PARAMETERS]?.toString(), getConfig(mavenProject))
    }
    val compilerId = if (mavenProject.packaging != "pom") mavenConfiguration.value.pluginConfiguration?.let { getCompilerId(it) } else null

    val ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl

    for (compilerExtension in MavenCompilerExtension.EP_NAME.extensions) {
      if (!mavenConfiguration.value.isEmpty() && compilerId == compilerExtension.mavenCompilerId) {
        importCompilerConfiguration(module, mavenConfiguration.value, compilerExtension, mavenProject)
      }
      else {
        // cleanup obsolete options
        (compilerExtension.getCompiler(project)?.options as? JpsJavaCompilerOptions)?.let {
          ideCompilerConfiguration.setAdditionalOptions(it, module, emptyList())
        }
      }

      if (project.getUserData(DEFAULT_COMPILER_IS_RESOLVED) != true &&
          modifiableModelsProvider.getUserData(DEFAULT_COMPILER_IS_SET) == null &&
          defaultCompilerId == compilerExtension.mavenCompilerId) {
        val backendCompiler = compilerExtension.getCompiler(project)
        val autoDetectCompiler = MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler
        MavenLog.LOG.debug("maven compiler autodetect = ", autoDetectCompiler)
        if (backendCompiler != null && ideCompilerConfiguration.defaultCompiler != backendCompiler && autoDetectCompiler) {
          if (ideCompilerConfiguration.registeredJavaCompilers.contains(backendCompiler)) {
            ideCompilerConfiguration.defaultCompiler = backendCompiler
          }
          else {
            LOG.error(backendCompiler.toString() + " is not registered.")
          }
        }
        if (compilerId == null && !mavenConfiguration.value.isEmpty()) {
          importCompilerConfiguration(module, mavenConfiguration.value, compilerExtension, mavenProject)
        }
        modifiableModelsProvider.putUserData(DEFAULT_COMPILER_IS_SET, true)
      }
    }
  }

  override fun postProcess(module: Module,
                           mavenProject: MavenProject,
                           changes: MavenProjectChanges,
                           modifiableModelsProvider: IdeModifiableModelsProvider) {
    module.project.putUserData(DEFAULT_COMPILER_IS_RESOLVED, null)
    configureCompilers(mavenProject, module)
  }

  private fun configureCompilers(mavenProject: MavenProject, module: Module) {
    val project = module.project
    val configuration = CompilerConfiguration.getInstance(project)
    if (java.lang.Boolean.TRUE != module.getUserData(IGNORE_MAVEN_COMPILER_TARGET_KEY)) {
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
      configuration.setBytecodeTargetLevel(module, level.toJavaVersion().toString())
    }
    module.putUserData(IGNORE_MAVEN_COMPILER_TARGET_KEY, java.lang.Boolean.FALSE)

    // Exclude src/main/archetype-resources

    // Exclude src/main/archetype-resources
    val dir = VfsUtil.findRelativeFile(mavenProject.directoryFile, "src", "main", "resources", "archetype-resources")
    if (dir != null && !configuration.isExcludedFromCompilation(dir)) {
      val cfg = configuration.excludedEntriesConfiguration
      cfg.addExcludeEntryDescription(ExcludeEntryDescription(dir, true, false, MavenDisposable.getInstance(project)))
    }
  }

  private fun importCompilerConfiguration(module: Module,
                                          mavenCompilerConfiguration: MavenCompilerConfiguration,
                                          extension: MavenCompilerExtension,
                                          mavenProject: MavenProject) {
    val compilerOptions = extension.getCompiler(module.project)?.options
    val compilerArgs = collectCompilerArgs(mavenCompilerConfiguration)
    extension.configureOptions(compilerOptions, module, mavenProject, compilerArgs)
  }


  companion object {
    private val COMPILERS = Key.create<MutableSet<String>>("maven.compilers")
    private val DEFAULT_COMPILER_IS_RESOLVED = Key.create<Boolean>("default.compiler.resolved")
    private val DEFAULT_COMPILER_IS_SET = Key.create<Boolean>("default.compiler.updated")
    private const val JAVAC_ID = "javac"
    private const val MAVEN_COMPILER_PARAMETERS = "maven.compiler.parameters"

    @JvmField
    val IGNORE_MAVEN_COMPILER_TARGET_KEY = Key.create<Boolean>("idea.maven.skip.compiler.target.level")

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
