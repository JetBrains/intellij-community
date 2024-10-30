// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
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
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.getEelApi
import com.intellij.platform.eel.provider.utils.fetchLoginShellEnvVariablesBlocking
import com.intellij.platform.eel.provider.utils.where
import com.intellij.platform.eel.toNioPath
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.ui.navigation.Place
import com.intellij.util.SystemProperties
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.jetbrains.idea.maven.config.MavenConfig
import org.jetbrains.idea.maven.config.MavenConfigSettings
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path
import javax.swing.event.HyperlinkEvent

object MavenEelUtil : MavenUtil() {
  @JvmStatic
  fun EelApi?.resolveM2Dir(): Path {
    return runBlockingMaybeCancellable { resolveM2DirAsync() }
  }

  @JvmStatic
  suspend fun EelApi?.resolveM2DirAsync(): Path {
    val localUserHome = Path.of(SystemProperties.getUserHome())
    val userHome = if (this != null) fs.user.home.let(mapper::toNioPath) else localUserHome

    return userHome.resolve(DOT_M2_DIR)
  }

  @JvmStatic
  suspend fun Project?.resolveM2DirAsync(): Path {
    return this?.getEelApi().resolveM2DirAsync()
  }

  suspend fun <T> resolveUsingEel(project: Project?, ordinary: suspend () -> T, eel: suspend (EelApi) -> T?): T {
    if (project == null && isMavenUnitTestModeEnabled()) {
      MavenLog.LOG.error("resolveEelAware: Project is null")
    }

    return project?.getEelApi()?.let { eel(it) } ?: ordinary.invoke()
  }

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
      collectMavenDirectories().firstNotNullOfOrNull(::getMavenHomePath)
    }

    return directory?.resolve(CONF_DIR)?.resolve(SETTINGS_XML)
  }

  @JvmStatic
  fun EelApi.collectMavenDirectories(): List<StaticResolvedMavenHomeType> {
    val result = ArrayList<StaticResolvedMavenHomeType>()

    val m2home = exec.fetchLoginShellEnvVariablesBlocking()[ENV_M2_HOME]
    if (m2home != null && !isEmptyOrSpaces(m2home)) {
      val homeFromEnv = fs.getPath(m2home).toNioPath(this)
      if (isValidMavenHome(homeFromEnv)) {
        MavenLog.LOG.debug("resolved maven home using \$M2_HOME as ${homeFromEnv}")
        result.add(MavenInSpecificPath(m2home))
      }
      else {
        MavenLog.LOG.debug("Maven home using \$M2_HOME is invalid")
      }
    }


    var home = fs.getPath("/usr/share/maven").toNioPath(this)
    if (isValidMavenHome(home)) {
      MavenLog.LOG.debug("Maven home found at /usr/share/maven")
      result.add(MavenInSpecificPath(home))
    }

    home = fs.getPath("/usr/share/maven2").toNioPath(this)
    if (isValidMavenHome(home)) {
      MavenLog.LOG.debug("Maven home found at /usr/share/maven2")
      result.add(MavenInSpecificPath(home))
    }

    val path = runBlockingMaybeCancellable { where("mvn") }?.toNioPath(this)?.parent?.parent
    if (path != null && isValidMavenHome(path)) {
      result.add(MavenInSpecificPath(path))
    }
    return result
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

  @JvmStatic
  fun <T> resolveUsingEelBlocking(project: Project?, ordinary: () -> T, eel: suspend (EelApi) -> T?): T {
    return runBlockingMaybeCancellable { resolveUsingEel(project, ordinary, eel) }
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
    return runBlockingMaybeCancellable { resolveUserSettingsPathAsync(overriddenUserSettingsFile, project) }
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
  fun getGlobalSettings(project: Project?, mavenHome: StaticResolvedMavenHomeType, mavenConfig: MavenConfig?): Path? {
    val filePath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS)
    if (filePath != null) return Path.of(filePath)
    return resolveUsingEelBlocking(project,
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

  private fun findOrDownloadNewJdkOverEel(
    project: Project,
    notification: Notification,
    listener: NotificationListener,
  ) {
    MavenCoroutineScopeProvider.getCoroutineScope(project).launch(Dispatchers.IO) {
      withBackgroundProgress(project, MavenProjectBundle.message("wsl.jdk.searching"), cancellable = false) {
        val eel = project.getEelApi()
        val sdkPath = service<JdkFinder>().suggestHomePaths(project).firstOrNull()
        if (sdkPath != null) {
          writeAction {
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
}