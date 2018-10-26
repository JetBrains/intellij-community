// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil.nullize
import com.intellij.util.containers.ContainerUtil.addIfNotNull
import com.intellij.util.text.nullize
import org.jdom.Element
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions

/**
 * @author Vladislav.Soroka
 */
class MavenCompilerImporter : MavenImporter("org.apache.maven.plugins", "maven-compiler-plugin") {
  private val LOG = Logger.getInstance("#org.jetbrains.idea.maven.importing.MavenCompilerImporter")

  override fun isApplicable(mavenProject: MavenProject?): Boolean {
    return super.isApplicable(mavenProject) && Registry.`is`("maven.import.compiler.arguments", true)
  }

  override fun processChangedModulesOnly(): Boolean {
    return false
  }

  override fun preProcess(module: Module,
                          mavenProject: MavenProject,
                          changes: MavenProjectChanges,
                          modifiableModelsProvider: IdeModifiableModelsProvider) {
    val config = getConfig(mavenProject) ?: return

    var compilers = modifiableModelsProvider.getUserData<MutableSet<String>>(COMPILERS)
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
    val compilers = modifiableModelsProvider.getUserData(COMPILERS)
    val defaultCompilerId = if (compilers != null && compilers.size == 1) compilers.first() else JAVAC_ID

    val mavenConfiguration: Lazy<Element?> = lazy { getConfig(mavenProject) }
    val compilerId = if (mavenProject.packaging != "pom") mavenConfiguration.value?.let { getCompilerId(it) } else null

    val ideCompilerConfiguration = CompilerConfiguration.getInstance(module.project) as CompilerConfigurationImpl

    for (compilerExtension in MavenCompilerExtension.EP_NAME.extensions) {
      if (mavenConfiguration.value != null && compilerId == compilerExtension.mavenCompilerId) {
        importCompilerConfiguration(module, mavenConfiguration.value!!, compilerExtension)
      }
      else {
        // cleanup obsolete options
        (compilerExtension.getCompiler(module.project)?.options as? JpsJavaCompilerOptions)?.let {
          ideCompilerConfiguration.setAdditionalOptions(it, module, emptyList())
        }
      }

      if (modifiableModelsProvider.getUserData(DEFAULT_COMPILER_IS_SET) == null && defaultCompilerId == compilerExtension.mavenCompilerId) {
        val backendCompiler = compilerExtension.getCompiler(module.project)
        if (backendCompiler != null && ideCompilerConfiguration.defaultCompiler != backendCompiler) {
          if (ideCompilerConfiguration.registeredJavaCompilers.contains(backendCompiler)) {
            ideCompilerConfiguration.defaultCompiler = backendCompiler
          }
          else {
            LOG.error(backendCompiler.toString() + " is not registered.")
          }
        }

        modifiableModelsProvider.putUserData(DEFAULT_COMPILER_IS_SET, true)
      }
    }
  }

  private fun importCompilerConfiguration(module: Module,
                                          compilerMavenConfiguration: Element,
                                          extension: MavenCompilerExtension) {
    val compilerOptions = extension.getCompiler(module.project)?.options as? JpsJavaCompilerOptions ?: return

    val options = mutableListOf<String>()
    val parameters = compilerMavenConfiguration.getChild("parameters")

    if (parameters?.textTrim?.toBoolean() == true) {
      options += "-parameters"
    }

    val compilerArguments = compilerMavenConfiguration.getChild("compilerArguments")
    if (compilerArguments != null) {

      val effectiveArguments = compilerArguments.children.map {
        val key = it.name.run { if (startsWith("-")) this else "-$this" }
        val value = it.textTrim.nullize()
        key to value
      }.toMap()

      effectiveArguments.forEach { key, value ->
        if (key.startsWith("-A") && value != null) {
          options.add("$key=$value")
        }
        else {
          options.add(key)
          addIfNotNull(options, value)
        }
      }
    }

    addIfNotNull(options, nullize(compilerMavenConfiguration.getChildTextTrim("compilerArgument")))

    val compilerArgs = compilerMavenConfiguration.getChild("compilerArgs")
    if (compilerArgs != null) {
      for (arg in compilerArgs.getChildren("arg")) {
        addIfNotNull(options, nullize(arg.textTrim))
      }
      for (compilerArg in compilerArgs.getChildren("compilerArg")) {
        addIfNotNull(options, nullize(compilerArg.textTrim))
      }
    }

    val compilerConfiguration = CompilerConfiguration.getInstance(module.project) as CompilerConfigurationImpl
    compilerConfiguration.setAdditionalOptions(compilerOptions, module, options)
  }

  companion object {
    private val COMPILERS = Key.create<MutableSet<String>>("maven.compilers")
    private val DEFAULT_COMPILER_IS_SET = Key.create<Boolean>("default.compiler.updated")
    private const val JAVAC_ID = "javac"

    private fun getCompilerId(config: Element): String {
      val compilerId = config.getChildTextTrim("compilerId")
      if (compilerId.isNullOrBlank() || JAVAC_ID == compilerId) return JAVAC_ID
      else return compilerId
    }
  }
}
