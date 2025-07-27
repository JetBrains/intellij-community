// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.VersionComparatorUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenAnnotationProcessorConfiguratorUtil.getProcessorArtifactInfos
import org.jetbrains.idea.maven.importing.MavenImportUtil.annotationProcessorOptions
import org.jetbrains.idea.maven.importing.MavenImportUtil.compilerConfigsForCompilePhase
import org.jetbrains.idea.maven.importing.MavenImportUtil.declaredAnnotationProcessors
import org.jetbrains.idea.maven.importing.MavenImportUtil.getAnnotationProcessorDirectory
import org.jetbrains.idea.maven.importing.MavenImportUtil.procMode
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator.*
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.function.Function

private const val PROFILE_PREFIX = "Annotation profile for "
const val MAVEN_DEFAULT_ANNOTATION_PROFILE: String = "Maven default annotation processors profile"
private const val DEFAULT_ANNOTATION_PATH_OUTPUT = "target/generated-sources/annotations"
private const val DEFAULT_TEST_ANNOTATION_OUTPUT = "target/generated-test-sources/test-annotations"
private const val DEFAULT_BSC_ANNOTATION_PATH_OUTPUT = "target/generated-sources/apt"
private const val DEFAULT_BSC_TEST_ANNOTATION_OUTPUT = "target/generated-sources/apt-test"
val MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE: String = MavenAnnotationProcessorConfiguratorUtil.getModuleProfileName(
  "maven-processor-plugin default configuration")
private val ANNOTATION_PROCESSOR_MODULE_NAMES = Key.create<MutableMap<MavenProject, MutableList<String>>>(
  "ANNOTATION_PROCESSOR_MODULE_NAMES")

private const val PLUGIN_GROUP_ID = "org.apache.maven.plugins"

private const val PLUGIN_ARTIFACT_ID = "maven-compiler-plugin"

@ApiStatus.Internal
class MavenAnnotationProcessorConfigurator : MavenApplicableConfigurator(PLUGIN_GROUP_ID,
                                                                         PLUGIN_ARTIFACT_ID), MavenWorkspaceConfigurator {
  override fun isApplicable(mavenProject: MavenProject): Boolean {
    return true
  }

  override fun beforeModelApplied(context: MutableModelContext) {
    val mavenProjectToModuleNamesCache: MutableMap<MavenId, List<String>> = HashMap()
    for (each in context.mavenProjectsWithModules.asIterable()) {
      val moduleNames = each.modules.mapNotNull { if (it.type.containsCode) it.module.name else null }
      mavenProjectToModuleNamesCache[each.mavenProject.mavenId] = moduleNames
    }

    val changedOnlyProjects = context.mavenProjectsWithModules.mapNotNull { if (it.hasChanges) it.mavenProject else null }

    val map = HashMap<MavenProject, MutableList<String>>()
    collectProcessorModuleNames(changedOnlyProjects.asIterable(),
                                { moduleName: MavenId -> mavenProjectToModuleNamesCache[moduleName] },
                                map)
    ANNOTATION_PROCESSOR_MODULE_NAMES[context] = map
  }

  private fun collectProcessorModuleNames(
    projects: Iterable<MavenProject>,
    moduleNameByProjectId: Function<MavenId, List<String>?>,
    result: MutableMap<MavenProject, MutableList<String>>,
  ) {
    for (mavenProject in projects) {
      if (!shouldEnableAnnotationProcessors(mavenProject)) continue

      val infos = ArrayList<MavenArtifactInfo>()

      mavenProject.compilerConfigsForCompilePhase()
        .mapNotNull { MavenJDOMUtil.findChildByPath(it, "annotationProcessorPaths") }
        .forEach { infos.addAll(getProcessorArtifactInfos(it, mavenProject)) }


      for (info in infos) {
        val mavenId = MavenId(info.groupId, info.artifactId, info.version)

        val processorModuleNames = moduleNameByProjectId.apply(mavenId)
        if (processorModuleNames != null) {
          result.computeIfAbsent(mavenProject) { _: MavenProject? -> ArrayList() }.addAll(processorModuleNames)
        }
      }
    }
  }


  override fun afterModelApplied(context: AppliedModelContext) {
    val nameToModuleCache: MutableMap<String, Module> = HashMap()
    for (each in context.mavenProjectsWithModules.asIterable()) {
      for (moduleWithType in each.modules) {
        val module = moduleWithType.module
        nameToModuleCache[module.name] = module
      }
    }

    val moduleByName = Function { moduleName: String -> nameToModuleCache[moduleName] }

    val perProjectProcessorModuleNames: Map<MavenProject, MutableList<String>> = ANNOTATION_PROCESSOR_MODULE_NAMES[context, java.util.Map.of()]

    val changedOnly = context.mavenProjectsWithModules.filter { it: MavenProjectWithModules<Module> -> it.hasChanges }
    val projectWithModules = changedOnly.map { it: MavenProjectWithModules<Module> ->
      val processorModuleNames = perProjectProcessorModuleNames.getOrDefault(it.mavenProject, listOf())
      MavenProjectWithProcessorModules(it.mavenProject, it.modules, processorModuleNames)
    }

    configureProfiles(context.project,
                      context.mavenProjectsTree,
                      projectWithModules.asIterable(),
                      moduleByName)
  }


  private class MavenProjectWithProcessorModules(
    val mavenProject: MavenProject,
    val mavenProjectModules: List<ModuleWithType<Module>>,
    val processorModuleNames: List<String>,
  )

  private fun configureProfiles(
    project: Project,
    tree: MavenProjectsTree,
    projectsWithModules: Iterable<MavenProjectWithProcessorModules>,
    moduleByName: Function<String, Module?>,
  ) {
    val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl

    for (it in projectsWithModules) {
      val rootProject = tree.findRootProject(it.mavenProject)

      for (moduleWithType in it.mavenProjectModules) {
        val module = moduleWithType.module
        val moduleType = moduleWithType.type

        if (!isLevelMoreThan6(module)) continue

        if (shouldEnableAnnotationProcessors(it.mavenProject) && moduleType.containsCode) {
          val processorModules = it.processorModuleNames.mapNotNull { moduleByName.apply(it) }
          val profileIsDefault = createOrUpdateProfile(it.mavenProject, module, processorModules, compilerConfiguration)

          if (profileIsDefault != null) {
            cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, profileIsDefault.first, profileIsDefault.second, module)
          }
        }
        else {
          cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, null, false, module)
        }
      }
    }
  }

  private fun createOrUpdateProfile(
    mavenProject: MavenProject,
    module: Module,
    processorModules: List<Module>,
    compilerConfiguration: CompilerConfigurationImpl,
  ): Pair<ProcessorConfigProfile, Boolean>? {
    val processors = mavenProject.declaredAnnotationProcessors
    val options = mavenProject.annotationProcessorOptions

    val outputRelativeToContentRoot: Boolean
    val annotationProcessorDirectory: String
    val testAnnotationProcessorDirectory: String

    if (MavenImportUtil.isMainOrTestModule(module)) {
      outputRelativeToContentRoot = false
      annotationProcessorDirectory = getAnnotationsDirectoryRelativeTo(mavenProject, false, mavenProject.outputDirectory)
      testAnnotationProcessorDirectory = getAnnotationsDirectoryRelativeTo(mavenProject, true, mavenProject.testOutputDirectory)
    }
    else {
      val mavenProjectDirectory = mavenProject.directory

      outputRelativeToContentRoot = true
      annotationProcessorDirectory = getAnnotationsDirectoryRelativeTo(mavenProject, false, mavenProjectDirectory)
      testAnnotationProcessorDirectory = getAnnotationsDirectoryRelativeTo(mavenProject, true, mavenProjectDirectory)
    }


    val annotationProcessorPath = getAnnotationProcessorPath(mavenProject, processorModules)

    val isDefault: Boolean
    val moduleProfileName: String

    val isDefaultSettings = (ContainerUtil.isEmpty(processors)
                             && options.isEmpty()
                             && StringUtil.isEmpty(annotationProcessorPath))
    if (isDefaultSettings && DEFAULT_ANNOTATION_PATH_OUTPUT == FileUtil.toSystemIndependentName(
        annotationProcessorDirectory) && DEFAULT_TEST_ANNOTATION_OUTPUT == FileUtil.toSystemIndependentName(
        testAnnotationProcessorDirectory)) {
      moduleProfileName = MAVEN_DEFAULT_ANNOTATION_PROFILE
      isDefault = true
    }
    else if (isDefaultSettings && DEFAULT_BSC_ANNOTATION_PATH_OUTPUT == FileUtil.toSystemIndependentName(
        annotationProcessorDirectory) && DEFAULT_BSC_TEST_ANNOTATION_OUTPUT == FileUtil.toSystemIndependentName(
        testAnnotationProcessorDirectory)) {
      moduleProfileName = MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE
      isDefault = true
    }
    else {
      moduleProfileName = MavenAnnotationProcessorConfiguratorUtil.getModuleProfileName(module.name)
      isDefault = false
    }

    val moduleProfile =
      getModuleProfile(module, mavenProject, compilerConfiguration, moduleProfileName, outputRelativeToContentRoot,
                       annotationProcessorDirectory, testAnnotationProcessorDirectory)
    if (moduleProfile == null) return null

    if (StringUtil.isNotEmpty(annotationProcessorPath)) {
      moduleProfile.isObtainProcessorsFromClasspath = false
      moduleProfile.setProcessorPath(annotationProcessorPath)
    }

    return Pair.pair(moduleProfile, isDefault)
  }

  private fun getModuleProfile(
    module: Module,
    mavenProject: MavenProject,
    compilerConfiguration: CompilerConfigurationImpl,
    moduleProfileName: String,
    outputRelativeToContentRoot: Boolean,
    annotationProcessorDirectory: String,
    testAnnotationProcessorDirectory: String,
  ): ProcessorConfigProfile? {
    var moduleProfile = compilerConfiguration.findModuleProcessorProfile(moduleProfileName)

    if (moduleProfile == null) {
      moduleProfile = compilerConfiguration.addNewProcessorProfile(moduleProfileName)
      moduleProfile.setEnabled(true)
    }
    if (!moduleProfile.isEnabled) return null

    moduleProfile.isObtainProcessorsFromClasspath = true

    moduleProfile.isOutputRelativeToContentRoot = outputRelativeToContentRoot
    moduleProfile.setGeneratedSourcesDirectoryName(annotationProcessorDirectory, false)
    moduleProfile.setGeneratedSourcesDirectoryName(testAnnotationProcessorDirectory, true)

    moduleProfile.clearProcessorOptions()
    for ((key, value) in mavenProject.annotationProcessorOptions) {
      moduleProfile.setOption(key, value)
    }

    moduleProfile.clearProcessors()
    val processors = mavenProject.declaredAnnotationProcessors
    for (processor in processors) {
      moduleProfile.addProcessor(processor)
    }

    moduleProfile.addModuleName(module.name)
    return moduleProfile
  }


  private fun isLevelMoreThan6(module: Module): Boolean {
    val sdk = ReadAction.compute<Sdk?, RuntimeException> { ModuleRootManager.getInstance(module).sdk }
    if (sdk != null) {
      val versionString = sdk.versionString
      val languageLevel = LanguageLevel.parse(versionString)
      if (languageLevel != null && languageLevel.isLessThan(LanguageLevel.JDK_1_6)) return false
    }
    return true
  }

  private fun getAnnotationProcessorPath(
    mavenProject: MavenProject,
    processorModules: List<Module>,
  ): String {
    val annotationProcessorPath = StringJoiner(File.pathSeparator)

    val resultAppender = Consumer { path: String? ->
      annotationProcessorPath.add(FileUtil.toSystemDependentName(
        path!!))
    }

    for (artifact in mavenProject.externalAnnotationProcessors) {
      resultAppender.consume(artifact.path)
    }

    for (module in processorModules) {
      val enumerator = OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().runtimeOnly().recursively()

      for (url in enumerator.classes().urls) {
        resultAppender.consume(JpsPathUtil.urlToPath(url))
      }
    }

    return annotationProcessorPath.toString()
  }

  private fun cleanAndMergeModuleProfiles(
    rootProject: MavenProject,
    compilerConfiguration: CompilerConfigurationImpl,
    moduleProfile: ProcessorConfigProfile?,
    isDefault: Boolean,
    module: Module,
  ) {
    val profiles: List<ProcessorConfigProfile> = ArrayList(compilerConfiguration.moduleProcessorProfiles)
    for (p in profiles) {
      if (p !== moduleProfile) {
        p.removeModuleName(module.name)
        if (p.moduleNames.isEmpty() && p.name.startsWith(PROFILE_PREFIX)) {
          compilerConfiguration.removeModuleProcessorProfile(p)
        }
      }

      if (!isDefault && moduleProfile != null && isSimilarProfiles(p, moduleProfile)) {
        moduleProfile.isEnabled = p.isEnabled
        val mavenProjectRootProfileName = MavenAnnotationProcessorConfiguratorUtil.getModuleProfileName(rootProject.displayName)
        var mergedProfile = compilerConfiguration.findModuleProcessorProfile(mavenProjectRootProfileName)
        if (mergedProfile == null) {
          mergedProfile = ProcessorConfigProfileImpl(moduleProfile)
          mergedProfile.setName(mavenProjectRootProfileName)
          compilerConfiguration.addModuleProcessorProfile(mergedProfile)
          mergedProfile.addModuleNames(p.moduleNames)
          p.clearModuleNames()
          compilerConfiguration.removeModuleProcessorProfile(p)
          moduleProfile.clearModuleNames()
          compilerConfiguration.removeModuleProcessorProfile(moduleProfile)
        }
        else if (p === mergedProfile || isSimilarProfiles(mergedProfile, moduleProfile)) {
          if (moduleProfile !== mergedProfile) {
            mergedProfile.addModuleNames(moduleProfile.moduleNames)
            moduleProfile.clearModuleNames()
            compilerConfiguration.removeModuleProcessorProfile(moduleProfile)
          }
          if (p !== mergedProfile) {
            mergedProfile.addModuleNames(p.moduleNames)
            p.clearModuleNames()
            compilerConfiguration.removeModuleProcessorProfile(p)
          }
        }
      }
    }
  }

  private fun isSimilarProfiles(profile1: ProcessorConfigProfile?, profile2: ProcessorConfigProfile?): Boolean {
    if (profile1 == null || profile2 == null) return false

    val p1 = ProcessorConfigProfileImpl(profile1)
    p1.name = "tmp"
    p1.isEnabled = true
    p1.clearModuleNames()
    val p2 = ProcessorConfigProfileImpl(profile2)
    p2.name = "tmp"
    p2.isEnabled = true
    p2.clearModuleNames()
    return p1 == p2
  }

  private fun getAnnotationsDirectoryRelativeTo(mavenProject: MavenProject, isTest: Boolean, relativeTo: String): String {
    val annotationProcessorDirectory = mavenProject.getAnnotationProcessorDirectory(isTest)
    val path = Path.of(annotationProcessorDirectory)
    if (!path.isAbsolute) {
      return annotationProcessorDirectory
    }

    return try {
      Path.of(relativeTo).relativize(path).toString()
    }
    catch (_: IllegalArgumentException) {
      if (isTest) DEFAULT_TEST_ANNOTATION_OUTPUT else DEFAULT_ANNOTATION_PATH_OUTPUT
    }
  }

  private fun shouldEnableAnnotationProcessors(mavenProject: MavenProject): Boolean {
    if ("pom" == mavenProject.packaging) return false

    return (mavenProject.procMode != MavenProject.ProcMode.NONE
            || mavenProject.findPlugin("org.bsc.maven", "maven-processor-plugin") != null)
  }
}

@ApiStatus.Internal
object MavenAnnotationProcessorConfiguratorUtil {
  fun getModuleProfileName(moduleName: @NlsSafe String): String {
    return PROFILE_PREFIX + moduleName
  }

  private fun getProcessorVersion(groupId: String, artifactId: String, version: String?, project: MavenProject): String? {
    if (version != null) return version
    val pluginVersion = project.findPlugin(PLUGIN_GROUP_ID, PLUGIN_ARTIFACT_ID)?.version ?: return null

    if (VersionComparatorUtil.compare(pluginVersion, "3.12.0") >= 0) {
      return project.findManagedDependencyVersion(groupId, artifactId)
    }
    return null
  }

  fun getProcessorArtifactInfos(config: Element, mavenProject: MavenProject): List<MavenArtifactInfo> {
    val artifacts: MutableList<MavenArtifactInfo> = ArrayList()
    val addToArtifacts = Consumer { path: Element ->
      val groupId = path.getChildTextTrim("groupId")
      val artifactId = path.getChildTextTrim("artifactId")
      val version = path.getChildTextTrim("version")
      val resolvedVersion = getProcessorVersion(groupId, artifactId, version, mavenProject)

      val classifier = path.getChildTextTrim("classifier")

      //String type = path.getChildTextTrim("type");
      artifacts.add(MavenArtifactInfo(groupId, artifactId, resolvedVersion, "jar", classifier))
    }

    for (path in config.getChildren("path")) {
      addToArtifacts.consume(path)
    }

    for (dependency in config.getChildren("dependency")) {
      addToArtifacts.consume(dependency)
    }

    for (annotationProcessorPath in config.getChildren("annotationProcessorPath")) {
      addToArtifacts.consume(annotationProcessorPath)
    }
    return artifacts
  }
}