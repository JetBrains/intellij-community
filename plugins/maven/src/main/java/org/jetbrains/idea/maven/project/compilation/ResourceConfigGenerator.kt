// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.compilation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.util.io.UnsyncByteArrayOutputStream
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import org.jdom.Element
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.MavenPropertyResolver
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider
import org.jetbrains.idea.maven.importing.MavenImportUtil.getMavenModuleType
import org.jetbrains.idea.maven.importing.MavenImportUtil.getModuleEntities
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.idea.maven.model.MavenResource
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsTree.Companion.getFilterExclusions
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.jetbrains.idea.maven.utils.ManifestBuilder
import org.jetbrains.idea.maven.utils.ManifestBuilder.ManifestBuilderException
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildValueByPath
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.jps.maven.model.impl.*
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

internal class ResourceConfigGenerator(
  private val fileIndex: ProjectFileIndex,
  private val mavenProjectsManager: MavenProjectsManager,
  private val transformer: RemotePathTransformerFactory.Transformer,
  private val projectConfig: MavenProjectConfiguration,
  private val mavenProject: MavenProject,
) {
  fun generateResourceConfig() {
    // do not add resource roots for 'pom' packaging projects
    if ("pom" == mavenProject.packaging) return

    val pomXml = mavenProject.file

    if (mavenProject.directoryFile != fileIndex.getContentRootForFile(pomXml)) return

    val project = mavenProjectsManager.project
    val storage = project.workspaceModel.currentSnapshot
    val moduleEntities = getModuleEntities(project, pomXml)

    for (moduleEntity in moduleEntities) {
      val moduleType = moduleEntity.getMavenModuleType()
      if (moduleType == StandardMavenModuleType.COMPOUND_MODULE) continue
      val module = storage.moduleMap.getDataByEntity(moduleEntity) ?: continue
      generate(module, moduleType)
    }
  }

  private fun generate(module: Module, moduleType: StandardMavenModuleType) {
    val moduleName = module.name

    val resourceConfig = MavenModuleResourceConfiguration()
    val projectId = mavenProject.mavenId
    resourceConfig.id = MavenIdBean(projectId.groupId, projectId.artifactId, projectId.version)

    val parentId = mavenProject.parentId
    if (parentId != null) {
      resourceConfig.parentId = MavenIdBean(parentId.groupId, parentId.artifactId, parentId.version)
    }
    resourceConfig.directory = transformer.toRemotePathOrSelf(FileUtil.toSystemIndependentName(mavenProject.directory))!!
    resourceConfig.delimitersPattern = MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject).pattern()
    for (entry in mavenProject.modelMap.entries) {
      val key = entry.key
      val value = entry.value
      resourceConfig.modelMap.put(key, transformer.toRemotePathOrSelf(value))
    }

    addEarModelMapEntries(mavenProject, resourceConfig.modelMap)

    val pluginConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin")
    resourceConfig.outputDirectory =
      transformer.toRemotePathOrSelf(getResourcesPluginGoalOutputDirectory(mavenProject, pluginConfiguration, "resources"))
    resourceConfig.testOutputDirectory =
      transformer.toRemotePathOrSelf(getResourcesPluginGoalOutputDirectory(mavenProject, pluginConfiguration, "testResources"))

    when (moduleType) {
      StandardMavenModuleType.SINGLE_MODULE -> {
        addResources(transformer, resourceConfig.resources, mavenProject.resources)
        addResources(transformer, resourceConfig.testResources, mavenProject.testResources)
      }
      StandardMavenModuleType.MAIN_ONLY -> {
        addResources(transformer, resourceConfig.resources, mavenProject.resources)
      }
      StandardMavenModuleType.TEST_ONLY -> {
        addResources(transformer, resourceConfig.testResources, mavenProject.testResources)
      }
      else -> {}
    }

    addWebResources(transformer, moduleName, projectConfig, mavenProject)
    addEjbClientArtifactConfiguration(moduleName, projectConfig, mavenProject)

    resourceConfig.filteringExclusions.addAll(getFilterExclusions(mavenProject))

    val properties: Properties = getFilteringProperties(mavenProject, mavenProjectsManager)
    for (propEntry in properties.entries) {
      resourceConfig.properties.put(propEntry.key as String?, transformer.toRemotePathOrSelf(propEntry.value as String?))
    }

    resourceConfig.escapeString = findChildValueByPath(pluginConfiguration, "escapeString", null)
    val escapeWindowsPaths = findChildValueByPath(pluginConfiguration, "escapeWindowsPaths")
    if (escapeWindowsPaths != null) {
      resourceConfig.escapeWindowsPaths = escapeWindowsPaths.toBoolean()
    }

    val overwrite = findChildValueByPath(pluginConfiguration, "overwrite")
    if (overwrite != null) {
      resourceConfig.overwrite = overwrite.toBoolean()
    }

    projectConfig.moduleConfigurations.put(moduleName, resourceConfig)
    generateManifest(mavenProject, module, resourceConfig)
  }

  companion object {
    private val LOG = Logger.getInstance(ResourceConfigGenerator::class.java)
    private val IDEA_MAVEN_DISABLE_MANIFEST: String? = System.getProperty("idea.maven.disable.manifest")

    private fun addEarModelMapEntries(mavenProject: MavenProject, modelMap: MutableMap<String?, String?>) {
      val pluginConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-ear-plugin")
      val skinnyWars = findChildValueByPath(pluginConfiguration, "skinnyWars", "false")
      modelMap.put("build.plugin.maven-ear-plugin.skinnyWars", skinnyWars)
    }

    private fun getResourcesPluginGoalOutputDirectory(
      mavenProject: MavenProject,
      pluginConfiguration: Element?,
      goal: String,
    ): String? {
      val goalConfiguration = mavenProject.getPluginGoalConfiguration("org.apache.maven.plugins", "maven-resources-plugin", goal)
      var outputDirectory = findChildValueByPath(goalConfiguration, "outputDirectory", null)
      if (outputDirectory == null) {
        outputDirectory = findChildValueByPath(pluginConfiguration, "outputDirectory", null)
      }
      return if (outputDirectory == null || FileUtil.isAbsolute(outputDirectory))
        outputDirectory
      else
        mavenProject.directory + '/' + outputDirectory
    }

    private fun generateManifest(
      mavenProject: MavenProject,
      module: Module,
      resourceConfig: MavenModuleResourceConfiguration,
    ) {
      if (mavenProject.isAggregator) return
      if (IDEA_MAVEN_DISABLE_MANIFEST.toBoolean()) {
        resourceConfig.manifest = null
        return
      }

      try {
        var jdkVersion: String? = null
        val sdk = ModuleRootManager.getInstance(module).getSdk()
        if (sdk != null && (sdk.getVersionString().also { jdkVersion = it }) != null) {
          val quoteIndex = jdkVersion!!.indexOf('"')
          if (quoteIndex != -1) {
            jdkVersion = jdkVersion.substring(quoteIndex + 1, jdkVersion.length - 1)
          }
        }

        val domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.file)
        if (domModel != null) {
          val outputStream = UnsyncByteArrayOutputStream()
          ManifestBuilder(mavenProject).withJdkVersion(jdkVersion).build().write(outputStream)
          val resolvedText = MavenPropertyResolver.resolve(outputStream.toString(), domModel)
          resourceConfig.manifest = Base64.getEncoder().encodeToString(resolvedText.toByteArray(StandardCharsets.UTF_8))
        }
        resourceConfig.classpath = ManifestBuilder.getClasspath(mavenProject)
      }
      catch (e: ManifestBuilderException) {
        LOG.warn("Unable to generate artifact manifest", e)
      }
      catch (e: Exception) {
        LOG.warn("Unable to save generated artifact manifest", e)
      }
    }

    private fun getFilteringProperties(
      mavenProject: MavenProject,
      mavenProjectsManager: MavenProjectsManager,
    ): Properties {
      val properties = Properties()

      for (each in mavenProject.filterPropertiesFiles) {
        try {
          FileInputStream(each).use { `in` ->
            properties.load(`in`)
          }
        }
        catch (_: IOException) {
        }
      }

      properties.putAll(mavenProject.properties)

      properties.setProperty("settings.localRepository", mavenProject.localRepositoryPath.toAbsolutePath().toString())

      val jreDir = MavenUtil.getModuleJreHome(mavenProjectsManager, mavenProject)
      if (jreDir != null) {
        properties.setProperty("java.home", jreDir)
      }

      val javaVersion = MavenUtil.getModuleJavaVersion(mavenProjectsManager, mavenProject)
      if (javaVersion != null) {
        properties.setProperty("java.version", javaVersion)
      }

      return properties
    }

    private fun addResources(
      transformer: RemotePathTransformerFactory.Transformer,
      container: MutableList<ResourceRootConfiguration>,
      resources: Collection<MavenResource>,
    ) {
      for (resource in resources) {
        val dir = resource.directory

        val props = ResourceRootConfiguration()
        props.directory = transformer.toRemotePathOrSelf(FileUtil.toSystemIndependentName(dir))!!

        val target = transformer.toRemotePathOrSelf(resource.targetPath)
        props.targetPath = if (target != null) FileUtil.toSystemIndependentName(target) else null

        props.isFiltered = resource.isFiltered
        props.includes.clear()
        for (include in resource.includes) {
          props.includes.add(include.trim { it <= ' ' })
        }
        props.excludes.clear()
        for (exclude in resource.excludes) {
          props.excludes.add(exclude.trim { it <= ' ' })
        }
        container.add(props)
      }
    }

    private fun addWebResources(
      transformer: RemotePathTransformerFactory.Transformer,
      moduleName: String,
      projectCfg: MavenProjectConfiguration,
      mavenProject: MavenProject,
    ) {
      val warCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-war-plugin")
      if (warCfg == null) return

      val filterWebXml = warCfg.getChildTextTrim("filteringDeploymentDescriptors").toBoolean()
      val webResources = warCfg.getChild("webResources")

      val webArtifactName = MavenUtil.getArtifactName("war", moduleName, true)

      var artifactResourceCfg = projectCfg.webArtifactConfigs[webArtifactName]
      if (artifactResourceCfg == null) {
        artifactResourceCfg = MavenWebArtifactConfiguration()
        artifactResourceCfg.moduleName = moduleName
        projectCfg.webArtifactConfigs.put(webArtifactName, artifactResourceCfg)
      }
      else {
        LOG.error("MavenWebArtifactConfiguration already exists.")
      }

      addSplitAndTrimmed(artifactResourceCfg.packagingIncludes, warCfg.getChildTextTrim("packagingIncludes"))
      addSplitAndTrimmed(artifactResourceCfg.packagingExcludes, warCfg.getChildTextTrim("packagingExcludes"))
      addConfigValues(artifactResourceCfg.nonFilteredFileExtensions, "nonFilteredFileExtensions", "nonFilteredFileExtension", warCfg)

      var warSourceDirectory = warCfg.getChildTextTrim("warSourceDirectory")
      if (warSourceDirectory == null) warSourceDirectory = "src/main/webapp"
      if (!FileUtil.isAbsolute(warSourceDirectory)) {
        warSourceDirectory = mavenProject.directory + '/' + warSourceDirectory
      }
      artifactResourceCfg.warSourceDirectory =
        transformer.toRemotePathOrSelf(FileUtil.toSystemIndependentName(warSourceDirectory.removeSuffix("/")))

      addSplitAndTrimmed(artifactResourceCfg.warSourceIncludes, warCfg.getChildTextTrim("warSourceIncludes"))
      addSplitAndTrimmed(artifactResourceCfg.warSourceExcludes, warCfg.getChildTextTrim("warSourceExcludes"))

      if (webResources != null) {
        for (resource in webResources.getChildren("resource")) {
          val r = ResourceRootConfiguration()
          var directory = resource.getChildTextTrim("directory")
          if (directory.isNullOrBlank()) continue

          if (!FileUtil.isAbsolute(directory)) {
            directory = mavenProject.directory + '/' + directory
          }

          r.directory = transformer.toRemotePathOrSelf(directory)!!
          r.isFiltered = resource.getChildTextTrim("filtering").toBoolean()

          r.targetPath = resource.getChildTextTrim("targetPath")

          addConfigValues(r.includes, "includes", "include", resource)
          addConfigValues(r.excludes, "excludes", "exclude", resource)

          artifactResourceCfg.webResources.add(r)
        }
      }

      if (filterWebXml) {
        val r = ResourceRootConfiguration()
        r.directory = transformer.toRemotePathOrSelf(warSourceDirectory)!!
        r.includes = mutableSetOf<String?>("WEB-INF/web.xml")
        r.isFiltered = true
        r.targetPath = ""
        artifactResourceCfg.webResources.add(r)
      }
    }

    private fun addConfigValues(collection: MutableCollection<String?>, tag: String?, subTag: String?, resource: Element) {
      val config = resource.getChild(tag)
      if (config != null) {
        for (value in config.getChildren(subTag)) {
          val text = value.textTrim
          if (!text.isEmpty()) {
            collection.add(text)
          }
        }
        if (config.getChildren(subTag).isEmpty()) {
          addSplitAndTrimmed(collection, config.textTrim)
        }
      }
    }

    private fun addSplitAndTrimmed(collection: MutableCollection<String?>, commaSeparatedList: String?) {
      if (commaSeparatedList != null) {
        for (s in StringUtil.split(commaSeparatedList, ",")) {
          collection.add(s.trim { it <= ' ' })
        }
      }
    }

    private fun addEjbClientArtifactConfiguration(
      moduleName: String,
      projectCfg: MavenProjectConfiguration,
      mavenProject: MavenProject,
    ) {
      val pluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-ejb-plugin")

      if (pluginCfg == null || !pluginCfg.getChildTextTrim("generateClient").toBoolean()) {
        return
      }

      val ejbClientCfg = MavenEjbClientConfiguration()

      val includes = pluginCfg.getChild("clientIncludes")
      if (includes != null) {
        for (include in includes.getChildren("clientInclude")) {
          val includeText = include.textTrim
          if (!includeText.isEmpty()) {
            ejbClientCfg.includes.add(includeText)
          }
        }
      }

      val excludes = pluginCfg.getChild("clientExcludes")
      if (excludes != null) {
        for (exclude in excludes.getChildren("clientExclude")) {
          val excludeText = exclude.textTrim
          if (!excludeText.isEmpty()) {
            ejbClientCfg.excludes.add(excludeText)
          }
        }
      }

      if (!ejbClientCfg.isEmpty) {
        projectCfg.ejbClientArtifactConfigs.put(MavenUtil.getEjbClientArtifactName(moduleName, true), ejbClientCfg)
      }
    }
  }
}
