// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ObjectUtils
import com.intellij.util.PathUtil
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.lang3.SystemUtils
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.MavenDisposable
import org.jetbrains.idea.maven.config.MavenConfigSettings
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.indices.MavenIndices
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager.Companion.getInstance
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.DummyMavenServerConnector.Companion.isDummy
import org.jetbrains.idea.maven.server.MavenServerManager.MavenServerConnectorFactory
import org.jetbrains.idea.maven.server.MavenServerManagerEx.Companion.stopConnectors
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenEelUtil.getLocalRepo
import org.jetbrains.idea.maven.utils.MavenEelUtil.getUserSettings
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.rmi.RemoteException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal class MavenServerManagerImpl : MavenServerManager {
  private val myMultimoduleDirToConnectorMap: MutableMap<String, MavenServerConnector> = HashMap()
  private val isShutdown = AtomicBoolean(false)

  //TODO: should be replaced by map, where key is the indexing directory. (local/wsl)
  private var myIndexingConnector: MavenIndexingConnectorImpl? = null
  private var myIndexerWrapper: MavenIndexerWrapper? = null

  private var eventListenerJar: Path? = null

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        isShutdown.set(true)
        closeAllConnectorsEventually()
      }
    })

    connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (MavenUtil.INTELLIJ_PLUGIN_ID == pluginDescriptor.pluginId.idString) {
          isShutdown.set(true)
          closeAllConnectorsEventually()
        }
      }
    })

    connection.subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
      override fun onProjectTrusted(project: Project) {
        val manager = MavenProjectsManager.getInstance(project)
        if (manager.isMavenizedProject) {
          MavenUtil.restartMavenConnectors(project, true, Predicate { it.isDummy() })
        }
      }

      override fun onProjectUntrusted(project: Project) {
        val manager = MavenProjectsManager.getInstance(project)
        if (manager.isMavenizedProject) {
          MavenUtil.restartMavenConnectors(project, true) { it.isDummy() }
        }
      }

      override fun onProjectTrustedFromNotification(project: Project) {
        val manager = MavenProjectsManager.getInstance(project)
        if (manager.isMavenizedProject) {
          MavenLog.LOG.info("onProjectTrustedFromNotification forceUpdateAllProjectsOrFindAllAvailablePomFiles")
          manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
        }
      }
    })
  }

  override fun getAllConnectors(): Collection<MavenServerConnector> {
    val set = Collections.newSetFromMap(IdentityHashMap<MavenServerConnector, Boolean>())
    synchronized(myMultimoduleDirToConnectorMap) {
      set.addAll(myMultimoduleDirToConnectorMap.values)
      if (myIndexingConnector != null) {
        set.add(myIndexingConnector!!)
      }
    }
    return set
  }

  override fun restartMavenConnectors(project: Project, wait: Boolean, condition: Predicate<MavenServerConnector>) {
    val connectorsToShutDown: MutableList<MavenServerConnector> = ArrayList()
    synchronized(myMultimoduleDirToConnectorMap) {
      getAllConnectors().forEach(
        Consumer { it: MavenServerConnector ->
          if (project == it.project && condition.test(it)) {
            val removedConnector = removeConnector(it)
            if (null != removedConnector) {
              connectorsToShutDown.add(removedConnector)
            }
          }
        })
    }
    MavenProjectsManager.getInstance(project).embeddersManager.reset()
    stopConnectors(project, wait, connectorsToShutDown)
  }

  private fun doGetConnector(project: Project, workingDirectory: String): MavenServerConnector {
    val multimoduleDirectory = MavenDistributionsCache.getInstance(project).getMultimoduleDirectory(workingDirectory)
    val settings = MavenWorkspaceSettingsComponent.getInstance(project).settings
    val jdk = getJdk(project, settings)

    var connector = doGetOrCreateConnector(project, multimoduleDirectory, jdk)
    if (connector.isNew()) {
      connector.connect()
    }
    else {
      if (!compatibleParameters(project, connector, jdk, multimoduleDirectory)) {
        MavenLog.LOG.info("[connector] $connector is incompatible, restarting")
        shutdownConnector(connector, false)
        connector = this.doGetOrCreateConnector(project, multimoduleDirectory, jdk)
        connector.connect()
      }
    }
    if (MavenLog.LOG.isTraceEnabled) {
      MavenLog.LOG.trace("[connector] get $connector")
    }
    return connector
  }

  @Deprecated("use suspend", ReplaceWith("getConnector"))
  override fun getConnectorBlocking(project: Project, workingDirectory: String): MavenServerConnector {
    var connector = doGetConnector(project, workingDirectory)
    if (!connector.pingBlocking()) {
      shutdownConnector(connector, true)
      connector = doGetConnector(project, workingDirectory)
    }
    return connector
  }

  override suspend fun getConnector(project: Project, workingDirectory: String): MavenServerConnector {
    var connector = blockingContext {
      doGetConnector(project, workingDirectory)
    }
    if (!connector.ping()) {
      shutdownConnector(connector, true)
      connector = doGetConnector(project, workingDirectory)
    }
    return connector
  }

  private fun doGetOrCreateConnector(
    project: Project,
    multimoduleDirectory: String,
    jdk: Sdk,
  ): MavenServerConnector {
    if (isShutdown.get()) {
      throw IllegalStateException("We are closed, sorry. No connectors anymore")
    }

    synchronized(myMultimoduleDirToConnectorMap) {
      var connector: MavenServerConnector?
      connector = myMultimoduleDirToConnectorMap[multimoduleDirectory]
      if (connector != null) return connector
      connector = findCompatibleConnector(project, jdk, multimoduleDirectory)
      if (connector != null) {
        MavenLog.LOG.debug("[connector] use existing connector for $connector")
        connector.addMultimoduleDir(multimoduleDirectory)
      }
      else {
        connector = registerNewConnector(project, jdk, multimoduleDirectory)
      }
      myMultimoduleDirToConnectorMap.put(multimoduleDirectory, connector)

      return connector
    }


  }

  private fun findCompatibleConnector(
    project: Project,
    jdk: Sdk,
    multimoduleDirectory: String,
  ): MavenServerConnector? {
    val distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multimoduleDirectory)
    val vmOptions = MavenDistributionsCache.getInstance(project).getVmOptions(multimoduleDirectory)
    for ((_, value) in myMultimoduleDirToConnectorMap) {
      if (value.project != project) continue
      if (Registry.`is`("maven.server.per.idea.project")) return value
      if (value.isCompatibleWith(jdk, vmOptions, distribution)) {
        return value
      }
    }

    return null
  }

  private fun registerNewConnector(
    project: Project,
    jdk: Sdk,
    multimoduleDirectory: String,
  ): MavenServerConnector {
    val distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multimoduleDirectory)
    val vmOptions = MavenDistributionsCache.getInstance(project).getVmOptions(multimoduleDirectory)
    val debugPort = freeDebugPort
    val connector: MavenServerConnector
    if (project.isTrusted() || project.isDefault) {
      val connectorFactory = ApplicationManager.getApplication().getService(
        MavenServerConnectorFactory::class.java)
      connector = connectorFactory.create(project, jdk, vmOptions, debugPort, distribution, multimoduleDirectory)
      MavenLog.LOG.debug("[connector] new maven connector $connector")
    }
    else {
      MavenLog.LOG.warn("Project $project not trusted enough. Will not start maven for it")
      connector = DummyMavenServerConnector(project, jdk, vmOptions, distribution, multimoduleDirectory)
    }
    registerDisposable(project, connector)
    return connector
  }

  private fun registerDisposable(project: Project, connector: MavenServerConnector) {
    Disposer.register(MavenDisposable.getInstance(project)) {
      ApplicationManager.getApplication().executeOnPooledThread(
        Callable { shutdownConnector(connector, false) })
    }
  }

  override fun dispose() {
    shutdownNow()
  }

  override fun shutdownConnector(connector: MavenServerConnector, wait: Boolean): Boolean {
    val connectorToStop = removeConnector(connector)
    if (connectorToStop == null) return false
    connectorToStop.stop(wait)
    return true
  }

  private fun removeConnector(connector: MavenServerConnector): MavenServerConnector? {
    synchronized(myMultimoduleDirToConnectorMap) {
      if (myIndexingConnector === connector) {
        myIndexingConnector = null
        myIndexerWrapper = null
        return connector
      }
      if (!myMultimoduleDirToConnectorMap.containsValue(connector)) {
        return null
      }
      myMultimoduleDirToConnectorMap.entries.removeIf { e: Map.Entry<String, MavenServerConnector> -> e.value === connector }
    }
    return connector
  }

  /**
   * use MavenUtil.restartMavenConnectors
   */
  @TestOnly
  override fun closeAllConnectorsAndWait() {
    shutdownNow()
  }

  private fun closeAllConnectorsEventually() {
    ApplicationManager.getApplication().executeOnPooledThread {
      shutdownNow()
    }
  }

  private fun shutdownNow() {
    var values: Collection<MavenServerConnector>
    synchronized(myMultimoduleDirToConnectorMap) {
      values = ArrayList(myMultimoduleDirToConnectorMap.values)
    }

    val indexingConnector = myIndexingConnector
    if (null != indexingConnector) {
      shutdownConnector(indexingConnector, true)
    }
    values.forEach(Consumer { c: MavenServerConnector -> shutdownConnector(c, true) })
  }


  override fun getMavenEventListener(): File {
    return getEventListenerJar()!!.toFile()
  }

  private fun getEventListenerJar(): Path? {
    if (eventListenerJar != null) {
      return eventListenerJar
    }
    val pluginFileOrDir = Path.of(PathUtil.getJarPathForClass(MavenServerManager::class.java))
    val root = pluginFileOrDir.parent
    if (pluginFileOrDir.isDirectory()) {
      eventListenerJar = eventSpyPathForLocalBuild
      if (!eventListenerJar!!.exists()) {
        MavenLog.LOG.warn("""
                            Event listener does not exist: Please run rebuild for maven modules:
                            community/plugins/maven/maven-event-listener
                            """.trimIndent()
        )
      }
    }
    else {
      eventListenerJar = root.resolve("maven-event-listener.jar")
      if (!eventListenerJar!!.exists()) {
        MavenLog.LOG.warn("Event listener does not exist at " + eventListenerJar +
                          ". It should be built as part of plugin layout process and bundled along with maven plugin jars")
      }
    }
    return eventListenerJar
  }

  override fun createEmbedder(
    project: Project,
    alwaysOnline: Boolean,
    multiModuleProjectDirectory: String,
  ): MavenEmbedderWrapper {
    return object : MavenEmbedderWrapper(project) {
      private var myConnector: MavenServerConnector? = null

      val createMutex = Mutex()

      @Throws(RemoteException::class)
      override suspend fun create(): MavenServerEmbedder {
        return createMutex.withLock { doCreate() }
      }

      @Throws(RemoteException::class)
      private suspend fun doCreate(): MavenServerEmbedder {
        var settings =
          convertSettings(project, MavenProjectsManager.getInstance(project).generalSettings, multiModuleProjectDirectory)
        if (alwaysOnline && settings.isOffline) {
          settings = settings.clone()
          settings.isOffline = false
        }

        val transformer = RemotePathTransformerFactory.createForProject(project)
        var sdkPath = MavenUtil.getSdkPath(ProjectRootManager.getInstance(project).projectSdk)
        if (sdkPath != null) {
          sdkPath = transformer.toRemotePath(sdkPath)
        }
        settings.projectJdk = sdkPath

        val forceResolveDependenciesSequentially = Registry.`is`("maven.server.force.resolve.dependencies.sequentially")
        val useCustomDependenciesResolver = Registry.`is`("maven.server.use.custom.dependencies.resolver")

        myConnector = this@MavenServerManagerImpl.getConnector(project, multiModuleProjectDirectory)
        return myConnector!!.createEmbedder(MavenEmbedderSettings(
          settings,
          transformer.toRemotePath(multiModuleProjectDirectory),
          forceResolveDependenciesSequentially,
          useCustomDependenciesResolver
        ))
      }

      @Synchronized
      override fun cleanup() {
        super.cleanup()
        if (myConnector != null) {
          shutdownConnector(myConnector!!, false)
        }
      }
    }
  }

  override fun createIndexer(): MavenIndexerWrapper {
    return createDedicatedIndexer()!!
  }

  override fun createIndexer(project: Project): MavenIndexerWrapper {
    return if (Registry.`is`("maven.dedicated.indexer")) {
      createDedicatedIndexer()!!
    }
    else {
      createLegacyIndexer(project)
    }
  }

  private fun createDedicatedIndexer(): MavenIndexerWrapper? {
    if (myIndexerWrapper != null) return myIndexerWrapper
    synchronized(myMultimoduleDirToConnectorMap) {
      if (myIndexerWrapper != null) return myIndexerWrapper
      val workingDir = SystemUtils.getUserHome().absolutePath
      myIndexerWrapper =
        object : MavenIndexerWrapper() {
          override fun createMavenIndices(project: Project): MavenIndices {
            val indices = MavenIndices(this, getInstance().getIndicesDir().toFile(), project)
            Disposer.register(MavenDisposable.getInstance(project), indices)
            return indices
          }

          @Throws(RemoteException::class)
          override fun createBlocking(): MavenServerIndexer {
            val indexingConnector = indexingConnector
            return indexingConnector!!.createIndexer()
          }

          @Throws(RemoteException::class)
          override suspend fun create(): MavenServerIndexer {
            val indexingConnector = indexingConnector
            return indexingConnector!!.createIndexer()
          }

          @Synchronized
          override fun handleRemoteError(e: RemoteException) {
            super.handleRemoteError(e)
            if (waitIfNotIdeaShutdown()) {
              val indexingConnector = myIndexingConnector
              if (indexingConnector != null && !indexingConnector.checkConnected()) {
                shutdownConnector(indexingConnector, true)
              }
            }
          }

          private val indexingConnector: MavenServerConnector?
            get() {
              if (myIndexingConnector != null) return myIndexingConnector
              val jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk
              synchronized(myMultimoduleDirToConnectorMap) {
                if (myIndexingConnector != null) return myIndexingConnector
                myIndexingConnector = MavenIndexingConnectorImpl(jdk,
                                                                 "",
                                                                 freeDebugPort,
                                                                 MavenDistributionsCache.resolveEmbeddedMavenHome(),
                                                                 workingDir)
              }
              myIndexingConnector!!.connect()
              return myIndexingConnector
            }

          private fun waitIfNotIdeaShutdown(): Boolean {
            try {
              Thread.sleep(100)
              return true
            }
            catch (ex: InterruptedException) {
              Thread.currentThread().interrupt()
            }
            return false
          }
        }
    }
    return myIndexerWrapper
  }

  private fun createLegacyIndexer(project: Project): MavenIndexerWrapper {
    var path = project.basePath
    if (path == null) {
      path = File(".").path
    }
    val finalPath = path
    return object : MavenIndexerWrapper() {
      override fun createMavenIndices(project: Project): MavenIndices {
        val indices = MavenIndices(this, getInstance().getIndicesDir().toFile(), project)
        Disposer.register(MavenDisposable.getInstance(project), indices)
        return indices
      }

      @Throws(RemoteException::class)
      override fun createBlocking(): MavenServerIndexer {
        var connector: MavenServerConnector?
        synchronized(myMultimoduleDirToConnectorMap) {
          connector = myMultimoduleDirToConnectorMap.values.find { c: MavenServerConnector ->
            c.multimoduleDirectories.find { mDir: String? ->
              FileUtil.isAncestor(finalPath!!, mDir!!, false)
            } != null
          }
        }
        if (connector != null) {
          return connector!!.createIndexer()
        }
        val workingDirectory = ObjectUtils.chooseNotNull<@SystemIndependent String?>(project.basePath,
                                                                                     SystemUtils.getUserHome().absolutePath)
        return getConnectorBlocking(project, workingDirectory!!).createIndexer()
      }

      @Throws(RemoteException::class)
      override suspend fun create(): MavenServerIndexer {
        var connector: MavenServerConnector?
        synchronized(myMultimoduleDirToConnectorMap) {
          connector = myMultimoduleDirToConnectorMap.values.find { c: MavenServerConnector ->
            c.multimoduleDirectories.find { mDir: String? ->
              FileUtil.isAncestor(finalPath!!, mDir!!, false)
            } != null
          }
        }
        if (connector != null) {
          return connector!!.createIndexer()
        }
        val workingDirectory = ObjectUtils.chooseNotNull<@SystemIndependent String?>(project.basePath,
                                                                                     SystemUtils.getUserHome().absolutePath)
        return getConnector(project, workingDirectory!!).createIndexer()
      }
    }
  }

  companion object {
    private val freeDebugPort: Int?
      get() {
        if (Registry.`is`("maven.server.debug")) {
          try {
            return NetUtils.findAvailableSocketPort()
          }
          catch (e: IOException) {
            MavenLog.LOG.warn(e)
          }
        }
        return null
      }

    private fun getJdk(project: Project, settings: MavenWorkspaceSettings): Sdk {
      val jdkForImporterName = settings.importingSettings.jdkForImporter
      var jdk: Sdk
      try {
        jdk = MavenUtil.getJdk(project, jdkForImporterName)
      }
      catch (e: ExternalSystemJdkException) {
        jdk = MavenUtil.getJdk(project, MavenRunnerSettings.USE_PROJECT_JDK)
        MavenProjectsManager.getInstance(project).syncConsole.addWarning(SyncBundle.message("importing.jdk.changed"),
                                                                         SyncBundle.message("importing.jdk.changed.description",
                                                                                            jdkForImporterName, jdk.name)
        )
      }
      if (JavaSdkVersionUtil.isAtLeast(jdk, JavaSdkVersion.JDK_1_8)) {
        return jdk
      }
      else {
        MavenLog.LOG.info("Selected jdk [" + jdk.name + "] is not JDK1.8+ Will use internal jdk instead")
        return JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk
      }
    }

    private fun compatibleParameters(
      project: Project,
      connector: MavenServerConnector,
      jdk: Sdk,
      multimoduleDirectory: String,
    ): Boolean {
      if (Registry.`is`("maven.server.per.idea.project")) return true
      val cache = MavenDistributionsCache.getInstance(project)
      val distribution = cache.getMavenDistribution(multimoduleDirectory)
      val vmOptions = cache.getVmOptions(multimoduleDirectory)
      return connector.isCompatibleWith(jdk, vmOptions, distribution)
    }

    private val eventSpyPathForLocalBuild: Path
      get() {
        val root = Path.of(PathUtil.getJarPathForClass(MavenServerManager::class.java))
        return root.parent.resolve("intellij.maven.server.eventListener")
      }

    private fun convertSettings(
      project: Project,
      settings: MavenGeneralSettings?,
      multiModuleProjectDirectory: String,
    ): MavenServerSettings {
      var settings = settings
      if (settings == null) {
        settings = MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings
      }
      val transformer = RemotePathTransformerFactory.createForProject(project)
      val result = MavenServerSettings()
      result.loggingLevel = settings!!.outputLevel.level
      result.isOffline = settings.isWorkOffline
      result.isUpdateSnapshots = settings.isAlwaysUpdateSnapshots
      val mavenDistribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(multiModuleProjectDirectory)

      val remotePath = transformer.toRemotePath(mavenDistribution.mavenHome.toString())
      result.mavenHomePath = remotePath

      val userSettings = getUserSettings(project, settings.userSettingsFile, settings.mavenConfig)
      val userSettingsPath = userSettings.toAbsolutePath().toString()
      result.userSettingsPath = transformer.toRemotePath(userSettingsPath)

      val localRepository =
        getLocalRepo(project, settings.localRepository, MavenInSpecificPath(mavenDistribution.mavenHome),
                     settings.userSettingsFile,
                     settings.mavenConfig).toAbsolutePath().toString()
      result.localRepositoryPath = transformer.toRemotePath(localRepository)
      var file = getGlobalConfigFromMavenConfig(project, settings, transformer)
      if (file == null) {
        file = MavenUtil.resolveGlobalSettingsFile(mavenDistribution.mavenHome.toFile())
      }
      result.globalSettingsPath = transformer.toRemotePath(file.absolutePath)
      return result
    }

    private fun getGlobalConfigFromMavenConfig(
      project: Project,
      settings: MavenGeneralSettings,
      transformer: RemotePathTransformerFactory.Transformer,
    ): File? {
      val mavenConfig = settings.mavenConfig
      if (mavenConfig == null) return null
      val filePath = mavenConfig.getFilePath(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS)
      if (filePath == null) return null
      return File(filePath)
    }
  }
}
