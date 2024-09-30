// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingMaybeCancellable
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
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.getEelApi
import com.intellij.platform.eel.provider.utils.userHomeBlocking
import com.intellij.ui.navigation.Place
import com.intellij.util.SystemProperties
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.idea.maven.config.MavenConfig
import org.jetbrains.idea.maven.config.MavenConfigSettings
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.event.HyperlinkEvent

object MavenEelUtil : MavenUtil() {
  @JvmStatic
  fun EelApi?.resolveM2Dir(): Path {
    val localUserHome = Path.of(SystemProperties.getUserHome())
    val userHome = if (this != null) fs.userHomeBlocking()?.let { mapper.toNioPath(it) } ?: localUserHome else localUserHome

    return userHome.resolve(DOT_M2_DIR)
  }

  suspend fun <T> resolveUsingEel(project: Project?, ordinary: Supplier<T>, eel: suspend (EelApi) -> T): T {
    if (project == null && isMavenUnitTestModeEnabled()) {
      MavenLog.LOG.error("resolveEelAware: Project is null")
    }
    val eelApi = project?.getEelApi() ?: return ordinary.get()
    return eel.invoke(eelApi)
  }

  @JvmStatic
  fun <T> resolveUsingEelBlocking(project: Project?, ordinary: Supplier<T>, eel: Function<EelApi, T>): T {
    return runBlockingMaybeCancellable { resolveUsingEel(project, ordinary) { eel.apply(it) } }
  }

  /**
   * USE ONLY IN SETTINGS PREVIEW
   */
  @JvmStatic
  fun getLocalRepoForUserPreview(
    project: Project?, overriddenLocalRepository: String?, mavenHome: MavenHomeType,
    mavenSettingsFile: String?, mavenConfig: MavenConfig?,
  ): Path {

    val staticMavenHome = mavenHome.staticOrBundled()
    return getLocalRepo(project, overriddenLocalRepository, staticMavenHome, mavenSettingsFile, mavenConfig)
  }

  @JvmStatic
  fun getLocalRepo(
    project: Project?, overriddenLocalRepository: String?, mavenHome: StaticResolvedMavenHomeType,
    mavenSettingsFile: String?, mavenConfig: MavenConfig?,
  ): Path {
    var settingPath = mavenSettingsFile
    if (mavenSettingsFile.isNullOrBlank()) {
      settingPath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_USER_SETTINGS) ?: ""
    }
    return resolveUsingEelBlocking(project,
                                   { resolveLocalRepository(project, overriddenLocalRepository, mavenHome, settingPath) },
                                   { resolveLocalRepository(project, overriddenLocalRepository, mavenHome, settingPath) })
  }

  @JvmStatic
  fun getUserSettings(project: Project?, userSettingsPath: String?, mavenConfig: MavenConfig?): Path {
    var settingPath = userSettingsPath
    if (userSettingsPath.isNullOrBlank()) {
      settingPath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_USER_SETTINGS) ?: ""
    }
    return resolveUsingEelBlocking(project,
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
    val jdkTask = object : Task.Backgroundable(null, MavenProjectBundle.message("wsl.jdk.searching"), false) {
      override fun run(indicator: ProgressIndicator) {
        val sdkPath = service<JdkFinder>().suggestHomePaths().firstOrNull()
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
        val model = JdkListDownloader.getInstance().downloadModelForJdkInstaller(indicator, JdkPredicate.default())
        if (model.isEmpty()) {
          Notification(MAVEN_NOTIFICATION_GROUP,
                       MavenProjectBundle.message("maven.wsl.jdk.fix.failed"),
                       MavenProjectBundle.message("maven.wsl.jdk.fix.failed.descr"),
                       NotificationType.ERROR).setListener(listener).notify(project)

        }
        else {
          this.title = MavenProjectBundle.message("wsl.jdk.downloading")
          // FIXME: really?? wslDistribution in the defaultInstallDir?
          val homeDir = installer.defaultInstallDir(model[0], null)
          val request = installer.prepareJdkInstallation(model[0], homeDir)
          installer.installJdk(request, indicator, project)
          notification.hideBalloon()
        }
      }
    }
    ProgressManager.getInstance().run(jdkTask)
  }
}