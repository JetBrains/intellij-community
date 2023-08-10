// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import org.jdom.Element
import org.jetbrains.idea.maven.importing.MavenPomPathModuleService.Companion.getInstance
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.importing.tree.dependency.*
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenLegacyModuleImporter(private val myModule: Module,
                                private val myMavenTree: MavenProjectsTree,
                                private val myMavenProject: MavenProject,
                                private val myMavenProjectToModuleName: Map<MavenProject, String>,
                                private val mySettings: MavenImportingSettings,
                                private val myModifiableModelsProvider: IdeModifiableModelsProvider) {
  private var myRootModelAdapter: MavenRootModelAdapter? = null

  init {
    val pomFile = myMavenProject.file
    if (!FileUtil.namesEqual("pom", pomFile.nameWithoutExtension)) {
      getInstance(myModule).pomFileUrl = pomFile.url
    }
  }

  fun config(mavenRootModelAdapter: MavenRootModelAdapter?) {
    myRootModelAdapter = mavenRootModelAdapter
    configFolders()
    configDependencies()
    configLanguageLevel()
  }

  fun config(mavenRootModelAdapter: MavenRootModelAdapter?, importData: MavenTreeModuleImportData) {
    myRootModelAdapter = mavenRootModelAdapter
    configFolders()
    configDependencies(importData.dependencies)
    val level = MavenImportUtil.getLanguageLevel(myMavenProject) { importData.moduleData.sourceLanguageLevel }
    configLanguageLevel(level)
  }

  class ExtensionImporter private constructor(private val myModule: Module,
                                              private val myMavenProjectsTree: MavenProjectsTree,
                                              private val myMavenProject: MavenProject,
                                              private val myMavenProjectChanges: MavenProjectChanges,
                                              private val myMavenProjectToModuleName: Map<MavenProject, String>,
                                              private val myImporters: List<MavenImporter>) {
    private var myRootModelAdapter: MavenRootModelAdapter? = null
    private var myModifiableModelsProvider: IdeModifiableModelsProvider? = null
    val isModuleDisposed: Boolean
      get() = myModule.isDisposed()

    fun init(ideModelsProvider: IdeModifiableModelsProvider) {
      myModifiableModelsProvider = ideModelsProvider
      myRootModelAdapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(myMavenProject, myModule, myModifiableModelsProvider))
    }

    private fun doConfigurationStep(step: Runnable) {
      MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), step)
    }

    fun preConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      doConfigurationStep { doPreConfig(counters) }
    }

    private fun doPreConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      if (myModule.isDisposed()) return
      val moduleType = ModuleType.get(myModule)
      for (importer in myImporters) {
        try {
          if (importer.moduleType === moduleType) {
            measureImporterTime(importer, counters, true) {
              importer.preProcess(myModule, myMavenProject, myMavenProjectChanges, myModifiableModelsProvider)
            }
          }
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenImporter.preConfig, skipping it.", e)
        }
      }
    }

    fun config(postTasks: List<MavenProjectsProcessorTask>, counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      doConfigurationStep { doConfig(postTasks, counters) }
    }

    private fun doConfig(postTasks: List<MavenProjectsProcessorTask>, counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      if (myModule.isDisposed()) return
      val moduleType = ModuleType.get(myModule)
      for (importer in myImporters) {
        if (importer.moduleType === moduleType) {
          try {
            measureImporterTime(importer, counters, false) {
              importer.process(
                myModifiableModelsProvider!!,
                myModule,
                myRootModelAdapter!!,
                myMavenProjectsTree,
                myMavenProject,
                myMavenProjectChanges,
                myMavenProjectToModuleName,
                postTasks)
            }
          }
          catch (e: Exception) {
            MavenLog.LOG.error("Exception in MavenImporter.config, skipping it.", e)
          }
        }
      }
    }

    fun postConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      doConfigurationStep { doPostConfig(counters) }
    }

    private fun doPostConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      if (myModule.isDisposed()) return
      val moduleType = ModuleType.get(myModule)
      for (importer in myImporters) {
        try {
          if (importer.moduleType === moduleType) {
            measureImporterTime(importer, counters, false) {
              importer.postProcess(myModule, myMavenProject, myMavenProjectChanges, myModifiableModelsProvider)
            }
          }
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenImporter.postConfig, skipping it.", e)
        }
      }
    }

    class CountAndTime {
      var count = 0
      var timeNano: Long = 0
    }

    companion object {
      @JvmStatic
      fun createIfApplicable(mavenProject: MavenProject,
                             module: Module,
                             moduleType: StandardMavenModuleType,
                             mavenTree: MavenProjectsTree,
                             changes: MavenProjectChanges,
                             mavenProjectToModuleName: Map<MavenProject, String>,
                             isWorkspaceImport: Boolean): ExtensionImporter? {
        if (moduleType === StandardMavenModuleType.COMPOUND_MODULE) return null
        var suitableImporters = MavenImporter.getSuitableImporters(mavenProject, isWorkspaceImport)

        // We must run all importers when we import into Workspace Model:
        //  in Workspace model the project is recreated from scratch. But for the importers for which processChangedModulesOnly = true,
        //  we don't know whether they rely on the fact, that previously imported data is kept in the project model on reimport.
        if (!isWorkspaceImport && !changes.hasChanges()) {
          suitableImporters = suitableImporters.filter { it: MavenImporter -> !it.processChangedModulesOnly() }
        }
        return if (suitableImporters.isEmpty()) null
        else ExtensionImporter(module, mavenTree, mavenProject, changes, mavenProjectToModuleName, suitableImporters)
      }

      private fun measureImporterTime(importer: MavenImporter,
                                      counters: MutableMap<Class<out MavenImporter>, CountAndTime>,
                                      increaseModuleCounter: Boolean,
                                      r: Runnable) {
        val before = System.nanoTime()
        try {
          r.run()
        }
        finally {
          val countAndTime = counters.computeIfAbsent(importer.javaClass) { _: Class<out MavenImporter>? -> CountAndTime() }
          if (increaseModuleCounter) countAndTime.count++
          countAndTime.timeNano += System.nanoTime() - before
        }
      }
    }
  }

  private fun configFolders() {
    MavenLegacyFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config()
  }

  private fun configDependencies() {
    val dependencyTypesFromSettings: MutableSet<String> = HashSet()
    if (!ReadAction.compute<Boolean, RuntimeException> {
        val project = myModule.getProject()
        if (project.isDisposed()) return@compute false
        dependencyTypesFromSettings.addAll(MavenProjectsManager.getInstance(project).importingSettings.getDependencyTypesAsSet())
        true
      }) {
      return
    }
    for (d in myMavenProject.dependencies) {
      var artifact = d
      val dependencyType = artifact.type
      if (!dependencyTypesFromSettings.contains(dependencyType)
          && !myMavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType)) {
        continue
      }
      val scope = selectScope(artifact.scope)
      val depProject = myMavenTree.findProject(artifact.mavenId)
      if (depProject != null) {
        if (depProject === myMavenProject) continue
        val moduleName = myMavenProjectToModuleName[depProject]
        if (moduleName == null || myMavenTree.isIgnored(depProject)) {
          val projectsArtifactInRepository = createCopyForLocalRepo(artifact, myMavenProject)
          myRootModelAdapter!!.addLibraryDependency(projectsArtifactInRepository, scope, myModifiableModelsProvider, myMavenProject)
        }
        else {
          val isTestJar = MavenConstants.TYPE_TEST_JAR == dependencyType || "tests" == artifact.classifier
          myRootModelAdapter!!.addModuleDependency(moduleName, scope, isTestJar)
          val buildHelperCfg = depProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact")
          buildHelperCfg?.let { addAttachArtifactDependency(it, scope, depProject, artifact) }
          val classifier = artifact.classifier
          if (classifier != null && IMPORTED_CLASSIFIERS.contains(classifier)
              && !isTestJar
              && "system" != artifact.scope
              && "false" != System.getProperty("idea.maven.classifier.dep")) {
            val a = createCopyForLocalRepo(artifact, myMavenProject)
            myRootModelAdapter!!.addLibraryDependency(a, scope, myModifiableModelsProvider, myMavenProject)
          }
        }
      }
      else if ("system" == artifact.scope) {
        myRootModelAdapter!!.addSystemDependency(artifact, scope)
      }
      else {
        if ("bundle" == dependencyType) {
          artifact = MavenArtifact(
            artifact.groupId,
            artifact.artifactId,
            artifact.version,
            artifact.baseVersion,
            "jar",
            artifact.classifier,
            artifact.scope,
            artifact.isOptional,
            "jar",
            null,
            myMavenProject.localRepository,
            false, false
          )
        }
        val libraryOrderEntry = myRootModelAdapter!!.addLibraryDependency(artifact, scope, myModifiableModelsProvider, myMavenProject)
        val projectId = ProjectId(artifact.groupId, artifact.artifactId, artifact.version)
        myModifiableModelsProvider.trySubstitute(myModule, libraryOrderEntry, projectId)
      }
    }
    configSurefirePlugin()
  }

  private fun configDependencies(dependencies: List<MavenImportDependency<*>>) {
    for (dependency in dependencies) {
      when (dependency) {
        is SystemDependency -> {
          myRootModelAdapter!!.addSystemDependency(dependency.artifact, dependency.getScope())
        }
        is LibraryDependency -> {
          myRootModelAdapter!!.addLibraryDependency(
            dependency.artifact, dependency.getScope(), myModifiableModelsProvider, myMavenProject)
        }
        is ModuleDependency -> {
          myRootModelAdapter!!.addModuleDependency(dependency.artifact, dependency.getScope(), dependency.isTestJar)
        }
        is BaseDependency -> {
          val artifact = dependency.artifact
          val libraryOrderEntry = myRootModelAdapter!!.addLibraryDependency(
            artifact, dependency.getScope(), myModifiableModelsProvider, myMavenProject)
          myModifiableModelsProvider.trySubstitute(
            myModule, libraryOrderEntry, ProjectId(artifact.groupId, artifact.artifactId, artifact.version))
        }
      }
    }
  }

  private fun configSurefirePlugin() {
    // Remove "maven-surefire-plugin urls" library created by previous version of IDEA.
    // todo remove this code after 01.06.2013
    val moduleLibraryTable = myRootModelAdapter!!.rootModel.getModuleLibraryTable()
    val library = moduleLibraryTable.getLibraryByName(SUREFIRE_PLUGIN_LIBRARY_NAME)
    if (library != null) {
      moduleLibraryTable.removeLibrary(library)
    }
  }

  //TODO: Rewrite
  private fun addAttachArtifactDependency(buildHelperCfg: Element,
                                          scope: DependencyScope,
                                          mavenProject: MavenProject,
                                          artifact: MavenArtifact) {
    var libraryModel: Library.ModifiableModel? = null
    for (artifactsElement in buildHelperCfg.getChildren("artifacts")) {
      for (artifactElement in artifactsElement.getChildren("artifact")) {
        val typeString = artifactElement.getChildTextTrim("type")
        if (typeString != null && typeString != "jar") continue
        var rootType = OrderRootType.CLASSES
        val classifier = artifactElement.getChildTextTrim("classifier")
        if ("sources" == classifier) {
          rootType = OrderRootType.SOURCES
        }
        else if ("javadoc" == classifier) {
          rootType = JavadocOrderRootType.getInstance()
        }
        val filePath = artifactElement.getChildTextTrim("file")
        if (StringUtil.isEmpty(filePath)) continue
        var file: VirtualFile? = VfsUtil.findRelativeFile(filePath, mavenProject.directoryFile) ?: continue
        file = JarFileSystem.getInstance().getJarRootForLocalFile(file!!)
        if (file == null) continue
        if (libraryModel == null) {
          val libraryName = getAttachedJarsLibName(artifact)
          var library = myModifiableModelsProvider.getLibraryByName(libraryName)
          if (library == null) {
            library = myModifiableModelsProvider.createLibrary(libraryName, MavenRootModelAdapter.getMavenExternalSource())
          }
          libraryModel = myModifiableModelsProvider.getModifiableLibraryModel(library)
          val entry = myRootModelAdapter!!.rootModel.addLibraryEntry(library!!)
          entry.setScope(scope)
        }
        libraryModel?.addRoot(file, rootType!!)
      }
    }
  }

  private fun configLanguageLevel() {
    if ("false".equals(System.getProperty("idea.maven.configure.language.level"), ignoreCase = true)) return
    val level = getLanguageLevel(myMavenProject)
    myRootModelAdapter!!.setLanguageLevel(level)
  }

  private fun configLanguageLevel(level: LanguageLevel) {
    if ("false".equals(System.getProperty("idea.maven.configure.language.level"), ignoreCase = true)) return
    myRootModelAdapter!!.setLanguageLevel(level)
  }

  companion object {
    const val SUREFIRE_PLUGIN_LIBRARY_NAME = "maven-surefire-plugin urls"
    @JvmField
    val IMPORTED_CLASSIFIERS = setOf("client")

    @JvmStatic
    fun createCopyForLocalRepo(artifact: MavenArtifact, project: MavenProject): MavenArtifact {
      return MavenArtifact(
        artifact.groupId,
        artifact.artifactId,
        artifact.version,
        artifact.baseVersion,
        artifact.type,
        artifact.classifier,
        artifact.scope,
        artifact.isOptional,
        artifact.extension,
        null,
        project.localRepository,
        false, false
      )
    }

    @JvmStatic
    fun getAttachedJarsLibName(artifact: MavenArtifact): String {
      var libraryName = artifact.getLibraryName()
      assert(libraryName.startsWith(MavenArtifact.MAVEN_LIB_PREFIX))
      libraryName = MavenArtifact.MAVEN_LIB_PREFIX + "ATTACHED-JAR: " + libraryName.substring(MavenArtifact.MAVEN_LIB_PREFIX.length)
      return libraryName
    }

    @JvmStatic
    fun selectScope(mavenScope: String?): DependencyScope {
      if (MavenConstants.SCOPE_RUNTIME == mavenScope) return DependencyScope.RUNTIME
      if (MavenConstants.SCOPE_TEST == mavenScope) return DependencyScope.TEST
      return if (MavenConstants.SCOPE_PROVIDED == mavenScope) DependencyScope.PROVIDED else DependencyScope.COMPILE
    }

    @Deprecated("use {@link MavenImportUtil#getSourceLanguageLevel(MavenProject)}")
    fun getLanguageLevel(mavenProject: MavenProject?): LanguageLevel {
      return MavenImportUtil.getSourceLanguageLevel(mavenProject!!)
    }

    @Deprecated("use {@link MavenImportUtil#getDefaultLevel(MavenProject)}")
    fun getDefaultLevel(mavenProject: MavenProject?): LanguageLevel {
      return MavenImportUtil.getDefaultLevel(mavenProject)
    }
  }
}
