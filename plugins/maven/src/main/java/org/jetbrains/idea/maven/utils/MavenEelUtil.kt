// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.impl.DummyProject
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JdkFinder
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.asNioPathOrNull
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.fetchLoginShellEnvVariablesBlocking
import com.intellij.platform.eel.where
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.ui.navigation.Place
import com.intellij.util.SystemProperties
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.config.MavenConfig
import org.jetbrains.idea.maven.config.MavenConfigSettings
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil.CONF_DIR
import org.jetbrains.idea.maven.utils.MavenUtil.DOT_M2_DIR
import org.jetbrains.idea.maven.utils.MavenUtil.ENV_M2_HOME
import org.jetbrains.idea.maven.utils.MavenUtil.MAVEN_NOTIFICATION_GROUP
import org.jetbrains.idea.maven.utils.MavenUtil.MAVEN_REPO_LOCAL
import org.jetbrains.idea.maven.utils.MavenUtil.PROP_FORCED_M2_HOME
import org.jetbrains.idea.maven.utils.MavenUtil.REPOSITORY_DIR
import org.jetbrains.idea.maven.utils.MavenUtil.SETTINGS_XML
import org.jetbrains.idea.maven.utils.MavenUtil.doResolveLocalRepository
import org.jetbrains.idea.maven.utils.MavenUtil.getMavenHomePath
import org.jetbrains.idea.maven.utils.MavenUtil.isEmptyOrSpaces
import org.jetbrains.idea.maven.utils.MavenUtil.isMavenUnitTestModeEnabled
import org.jetbrains.idea.maven.utils.MavenUtil.isValidMavenHome
import org.jetbrains.idea.maven.utils.MavenUtil.resolveGlobalSettingsFile
import org.jetbrains.idea.maven.utils.MavenUtil.resolveUserSettingsPath
import java.io.IOException
import java.nio.file.Path
import javax.swing.event.HyperlinkEvent

object MavenEelUtil {
  @JvmStatic
  fun EelApi?.resolveM2Dir(): Path {
    val localUserHome = Path.of(SystemProperties.getUserHome())
    val mavenUserHomeVar = Environment.getVariable("MAVEN_USER_HOME")
    if (mavenUserHomeVar == null) {
      val userHome = if (this != null && this !is LocalEelApi) fs.user.home.asNioPath() else localUserHome

      return userHome.resolve(DOT_M2_DIR)
    }
    else {
      if (this != null && this !is LocalEelApi) {
        return fs.getPath(mavenUserHomeVar).asNioPath()
      }
      else return Path.of(mavenUserHomeVar)
    }

  }

  @JvmStatic
  suspend fun Project?.resolveM2DirAsync(): Path {
    return this?.filterAcceptable()?.getEelDescriptor()?.upgrade().resolveM2Dir()
  }

  suspend fun <T> resolveUsingEel(project: Project?, ordinary: suspend () -> T, eel: suspend (EelApi) -> T?): T {
    if (project == null && isMavenUnitTestModeEnabled()) {
      MavenLog.LOG.error("resolveEelAware: Project is null")
    }

    return project?.filterAcceptable()?.getEelDescriptor()?.upgrade()?.let { eel(it) } ?: ordinary.invoke()
  }

  private fun Project.filterAcceptable(): Project? = takeIf { !it.isDefault && it !is DummyProject }

  @JvmStatic
  fun EelApi.resolveUserSettingsFile(overriddenUserSettingsFile: String?): Path {
    if (overriddenUserSettingsFile.isNullOrEmpty()) {
      return resolveM2Dir().resolve(SETTINGS_XML)
    }
    else {
      return Path.of(overriddenUserSettingsFile)
    }
  }

  @JvmStatic
  fun EelApi.resolveGlobalSettingsFile(mavenHomeType: StaticResolvedMavenHomeType): Path? {
    val directory = if (this is LocalEelApi) {
      getMavenHomePath(mavenHomeType)
    }
    else {
      findMavenDistribution()?.let { getMavenHomePath(it) }
    }

    return directory?.resolve(CONF_DIR)?.resolve(SETTINGS_XML)
  }

  fun EelApi.findMavenDistribution(): MavenInSpecificPath? {
    return tryMavenRootFromEnvironment()
           ?: fs.tryMavenRoot("/usr/share/maven")
           ?: fs.tryMavenRoot("/usr/share/maven2")
           ?: tryMavenFromPath()
  }

  @JvmStatic
  fun EelApi.resolveRepository(
    overriddenRepository: String?,
    mavenHome: StaticResolvedMavenHomeType,
    overriddenUserSettingsFile: String?,
  ): Path {
    if (overriddenRepository != null && !isEmptyOrSpaces(overriddenRepository)) {
      return Path.of(overriddenRepository)
    }
    return doResolveLocalRepository(
      this.resolveUserSettingsFile(overriddenUserSettingsFile),
      this.resolveGlobalSettingsFile(mavenHome)
    ) ?: resolveM2Dir().resolve(REPOSITORY_DIR)
  }


  /**
   * USE ONLY IN SETTINGS PREVIEW
   */
  @JvmStatic
  fun getLocalRepoForUserPreview(
    project: Project,
    overriddenLocalRepository: String?,
    mavenHome: MavenHomeType,
    mavenSettingsFile: String?,
    mavenConfig: MavenConfig?,
  ): Path {
    val staticMavenHome = mavenHome.staticOrBundled()
    return getLocalRepoUnderModalProgress(project, overriddenLocalRepository, staticMavenHome, mavenSettingsFile, mavenConfig)
  }

  @JvmStatic
  fun getLocalRepo(
    project: Project?,
    overriddenLocalRepository: String?,
    mavenHome: StaticResolvedMavenHomeType,
    mavenSettingsFile: String?,
    mavenConfig: MavenConfig?,
  ): Path {
    return runBlockingMaybeCancellable { getLocalRepoAsync(project, overriddenLocalRepository, mavenHome, mavenSettingsFile, mavenConfig) }
  }

  @JvmStatic
  fun getLocalRepoUnderModalProgress(
    project: Project,
    overriddenLocalRepository: String?,
    mavenHome: StaticResolvedMavenHomeType,
    mavenSettingsFile: String?,
    mavenConfig: MavenConfig?,
  ): Path {
    return runWithModalProgressBlocking(project, MavenConfigurableBundle.message("maven.progress.title.computing.repository.location")) {
      getLocalRepoAsync(project, overriddenLocalRepository, mavenHome, mavenSettingsFile, mavenConfig)
    }
  }

  @JvmStatic
  suspend fun getLocalRepoAsync(
    project: Project?,
    overriddenLocalRepository: String?,
    mavenHome: StaticResolvedMavenHomeType,
    mavenSettingsFile: String?,
    mavenConfig: MavenConfig?,
  ): Path {
    var settingPath = mavenSettingsFile
    if (mavenSettingsFile.isNullOrBlank()) {
      settingPath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_USER_SETTINGS) ?: ""
    }
    return resolveUsingEel(project,
                           { resolveLocalRepositoryAsync(project, overriddenLocalRepository, mavenHome, settingPath) },
                           { if (it is LocalEelApi) null else it.resolveRepository(overriddenLocalRepository, mavenHome, settingPath) })
  }

  @JvmStatic
  fun resolveLocalRepositoryBlocking(
    project: Project?,
    overriddenLocalRepository: String?,
    mavenHomeType: StaticResolvedMavenHomeType,
    overriddenUserSettingsFile: String?,
  ): Path {
    return runBlockingMaybeCancellable { resolveLocalRepositoryAsync(project, overriddenLocalRepository, mavenHomeType, overriddenUserSettingsFile) }
  }

  suspend fun resolveUserSettingsPathAsync(overriddenUserSettingsFile: String?, project: Project?): Path {
    if (!overriddenUserSettingsFile.isNullOrEmpty()) return Path.of(overriddenUserSettingsFile)
    return project.resolveM2DirAsync().resolve(SETTINGS_XML)
  }

  @JvmStatic
  fun resolveUserSettingsPathBlocking(overriddenUserSettingsFile: String?, project: Project?): Path {
    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      resolveUserSettingsPathAsync(overriddenUserSettingsFile, project)
    }
  }

  suspend fun resolveLocalRepositoryAsync(
    project: Project?,
    overriddenLocalRepository: String?,
    mavenHomeType: StaticResolvedMavenHomeType,
    overriddenUserSettingsFile: String?,
  ): Path {
    val forcedM2Home = System.getProperty(PROP_FORCED_M2_HOME)
    if (forcedM2Home != null) {
      MavenLog.LOG.error("$PROP_FORCED_M2_HOME is deprecated, use maven.repo.local property instead")
      return Path.of(forcedM2Home)
    }

    val result: Path = if (!overriddenLocalRepository.isNullOrBlank()) {
      Path.of(overriddenLocalRepository)
    }
    else {
      val localRepoHome = System.getProperty(MAVEN_REPO_LOCAL)
      if (localRepoHome != null) {
        MavenLog.LOG.debug("Using $MAVEN_REPO_LOCAL=$localRepoHome")
        return Path.of(localRepoHome)
      }
      else {
        doResolveLocalRepository(
          resolveUserSettingsPathAsync(overriddenUserSettingsFile, project),
          resolveGlobalSettingsFile(mavenHomeType)
        ) ?: project.resolveM2DirAsync().resolve(REPOSITORY_DIR)
      }
    }

    return try {
      result.toRealPath()
    }
    catch (e: IOException) {
      result
    }
  }

  @JvmStatic
  fun getUserSettingsUnderModalProgress(project: Project, userSettingsPath: String?, mavenConfig: MavenConfig?): Path {
    return runWithModalProgressBlocking(project, MavenConfigurableBundle.message("maven.progress.title.computing.user.settings.location")) {
      getUserSettingsAsync(project, userSettingsPath, mavenConfig)
    }
  }

  @JvmStatic
  fun getUserSettings(project: Project?, userSettingsPath: String?, mavenConfig: MavenConfig?): Path {
    return runBlockingMaybeCancellable { getUserSettingsAsync(project, userSettingsPath, mavenConfig) }
  }

  @JvmStatic
  suspend fun getUserSettingsAsync(project: Project?, userSettingsPath: String?, mavenConfig: MavenConfig?): Path {
    var settingPath = userSettingsPath
    if (userSettingsPath.isNullOrBlank()) {
      settingPath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_USER_SETTINGS) ?: ""
    }
    return resolveUsingEel(project,
                           { resolveUserSettingsPath(settingPath, project) },
                           { resolveUserSettingsPath(settingPath, project) })
  }

  @JvmStatic
  suspend fun getGlobalSettingsAsync(project: Project?, mavenHome: StaticResolvedMavenHomeType, mavenConfig: MavenConfig?): Path? {
    val filePath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS)
    if (filePath != null) return Path.of(filePath)
    return resolveUsingEel(project,
                           { resolveGlobalSettingsFile(mavenHome) },
                           { resolveGlobalSettingsFile(mavenHome) })
  }

  @JvmStatic
  fun restartMavenConnectorsIfJdkIncorrect(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          var needReset = false

          MavenServerManager.getInstance().getAllConnectors().forEach {
            if (it.project == project) {
              needReset = true
              MavenServerManager.getInstance().shutdownConnector(it, true)
            }
          }
          if (!needReset) {
            MavenProjectsManager.getInstance(project).embeddersManager.reset()
          }

        }, SyncBundle.message("maven.sync.restarting"), false, project)
    }

  }

  @JvmStatic
  fun checkJdkAndShowNotification(project: Project) {
    val sdk = ProjectRootManager.getInstance(project).projectSdk
    if (sdk == null) return
    val FIX_STR = "FIX"
    val OPEN_STR = "OPEN"

    val listener = object : NotificationListener {
      override fun hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
        if (event.description == OPEN_STR) {
          val configurable = ProjectStructureConfigurable.getInstance(
            project)
          ShowSettingsUtil.getInstance().editConfigurable(project, configurable) {
            val place = Place().putPath(
              ProjectStructureConfigurable.CATEGORY, configurable.projectConfig)
            configurable.navigateTo(place, true)
          }
        }
        else {
          if (trySetUpExistingJdk(project, notification)) return
          ApplicationManager.getApplication().invokeLater {
            findOrDownloadNewJdk(project, sdk, notification, this)
          }
        }
      }
    }
  }

  private fun trySetUpExistingJdk(project: Project, notification: Notification): Boolean {
    val sdk = service<ProjectJdkTable>().allJdks.maxWithOrNull(compareBy(VersionComparatorUtil.COMPARATOR) { it.versionString })
    if (sdk == null) return false
    WriteAction.runAndWait<RuntimeException> {
      ProjectRootManagerEx.getInstance(project).projectSdk = sdk
      notification.hideBalloon()
    }
    return true
  }

  private fun findOrDownloadNewJdk(
    project: Project,
    sdk: Sdk,
    notification: Notification,
    listener: NotificationListener,
  ) {
    if (Registry.`is`("java.home.finder.use.eel")) {
      return findOrDownloadNewJdkOverEel(project, notification, listener)
    }

    val projectWslDistr = tryGetWslDistribution(project)

    val jdkTask = object : Task.Backgroundable(null, MavenProjectBundle.message("wsl.jdk.searching"), false) {
      override fun run(indicator: ProgressIndicator) {
        val sdkPath = service<JdkFinder>().suggestHomePaths().filter {
          sameDistributions(projectWslDistr, WslPath.getDistributionByWindowsUncPath(it))
        }.firstOrNull()
        if (sdkPath != null) {
          WriteAction.runAndWait<RuntimeException> {
            val jdkName = SdkConfigurationUtil.createUniqueSdkName(JavaSdk.getInstance(), sdkPath,
                                                                   ProjectJdkTable.getInstance().allJdks.toList())
            val newJdk = JavaSdk.getInstance().createJdk(jdkName, sdkPath)
            ProjectJdkTable.getInstance().addJdk(newJdk)
            ProjectRootManagerEx.getInstance(project).projectSdk = newJdk
            notification.hideBalloon()
          }
          return
        }
        val installer = JdkInstaller.getInstance()
        val jdkPredicate = when {
          projectWslDistr != null -> JdkPredicate.forWSL()
          else -> JdkPredicate.default()
        }
        val model = JdkListDownloader.getInstance().downloadModelForJdkInstaller(indicator, jdkPredicate)
        if (model.isEmpty()) {
          Notification(MAVEN_NOTIFICATION_GROUP,
                       MavenProjectBundle.message("maven.wsl.jdk.fix.failed"),
                       MavenProjectBundle.message("maven.wsl.jdk.fix.failed.descr"),
                       NotificationType.ERROR).setListener(listener).notify(project)

        }
        else {
          this.title = MavenProjectBundle.message("wsl.jdk.downloading")
          val homeDir = installer.defaultInstallDir(model[0], null, projectWslDistr)
          val request = installer.prepareJdkInstallation(model[0], homeDir)
          installer.installJdk(request, indicator, project)
          notification.hideBalloon()
        }
      }
    }
    ProgressManager.getInstance().run(jdkTask)
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineService(val coroutineScope: CoroutineScope)

  private fun findOrDownloadNewJdkOverEel(
    project: Project,
    notification: Notification,
    listener: NotificationListener,
  ) {
    project.service<CoroutineService>().coroutineScope.launch(Dispatchers.IO) {
      withBackgroundProgress(project, MavenProjectBundle.message("wsl.jdk.searching"), cancellable = false) {
        val eel = project.getEelDescriptor().upgrade()
        val sdkPath = service<JdkFinder>().suggestHomePaths(project).firstOrNull()
        if (sdkPath != null) {
          edtWriteAction {
            val jdkName = SdkConfigurationUtil.createUniqueSdkName(JavaSdk.getInstance(), sdkPath,
                                                                   ProjectJdkTable.getInstance().allJdks.toList())
            val newJdk = JavaSdk.getInstance().createJdk(jdkName, sdkPath)
            ProjectJdkTable.getInstance().addJdk(newJdk)
            ProjectRootManagerEx.getInstance(project).projectSdk = newJdk
            notification.hideBalloon()
          }
          return@withBackgroundProgress
        }
        val installer = JdkInstaller.getInstance()
        val jdkPredicate = JdkPredicate.forEel(eel)
        val model = coroutineToIndicator {
          JdkListDownloader.getInstance().downloadModelForJdkInstaller(ProgressManager.getGlobalProgressIndicator(), jdkPredicate)
        }
        if (model.isEmpty()) {
          Notification(
            MAVEN_NOTIFICATION_GROUP,
            MavenProjectBundle.message("maven.wsl.jdk.fix.failed"),
            MavenProjectBundle.message("maven.wsl.jdk.fix.failed.descr"),
            NotificationType.ERROR
          ).setListener(listener).notify(project)

        }
        else {
          withProgressText(MavenProjectBundle.message("wsl.jdk.downloading")) {
            val homeDir = installer.defaultInstallDir(model[0], eel, null)
            val request = installer.prepareJdkInstallation(model[0], homeDir)
            coroutineToIndicator {
              installer.installJdk(request, ProgressManager.getGlobalProgressIndicator(), project)
            }
            notification.hideBalloon()
          }
        }
      }
    }
  }

  @JvmStatic
  @Deprecated("Use EEL API")
  private fun tryGetWslDistribution(project: Project): WSLDistribution? {
    return project.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) }
  }

  @JvmStatic
  @Deprecated("Use EEL API")
  private fun sameDistributions(first: WSLDistribution?, second: WSLDistribution?): Boolean {
    return first?.id == second?.id
  }

  private fun EelApi.tryMavenRootFromEnvironment(): MavenInSpecificPath? {
    val home = exec.fetchLoginShellEnvVariablesBlocking()[ENV_M2_HOME] ?: return null
    if (home.isNotBlank()) {
      return fs.tryMavenRoot(home)
    }
    return null
  }

  private fun EelApi.tryMavenFromPath(): MavenInSpecificPath? {
    val path = runBlockingMaybeCancellable { exec.where("mvn") } ?: return null
    val mavenHome = path.asNioPathOrNull()?.parent?.parent?.toString() ?: return null
    return fs.tryMavenRoot(mavenHome)
  }

  private fun EelFileSystemApi.tryMavenRoot(path: String): MavenInSpecificPath? {
    val home = getPath(path).asNioPath()
    if (isValidMavenHome(home)) {
      MavenLog.LOG.debug("Maven home found at $path")
      return MavenInSpecificPath(home)
    }
    return null
  }
}