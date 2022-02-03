// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.build.events.MessageEvent
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.InvalidSdkException
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.navigation.Place
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.idea.maven.config.MavenConfig
import org.jetbrains.idea.maven.config.MavenConfigSettings
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.CannotStartServerException
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.WslMavenDistribution
import org.jetbrains.idea.maven.server.wsl.BuildIssueWslJdk
import java.io.File
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.event.HyperlinkEvent

object MavenWslUtil : MavenUtil() {
  @JvmStatic
  fun getWslJdk(project: Project, name: String): Sdk {
    val projectWslDistr = tryGetWslDistribution(project) ?: throw IllegalStateException("project $project is not WSL based");
    if (name == MavenRunnerSettings.USE_JAVA_HOME) {
      val jdk =MavenWslCache.getInstance().wslEnv(projectWslDistr)?.get("JAVA_HOME")?.let { projectWslDistr.getWindowsPath(it) }?.let {
        JavaSdk.getInstance().createJdk("", it)
      }
      if (jdk != null && jdk.sdkType is JavaSdkType) {
        return jdk
      }
    }

    if (name == MavenRunnerSettings.USE_PROJECT_JDK) {
      val jdk = ProjectRootManager.getInstance(project).projectSdk
      if (jdk != null && jdk.sdkType is JavaSdkType && projectWslDistr == tryGetWslDistributionForPath(jdk.homePath)) {
        return jdk
      } else {
        MavenProjectsManager.getInstance(project).syncConsole.addBuildIssue(BuildIssueWslJdk(), MessageEvent.Kind.ERROR);
        throw InvalidSdkException(name)
      }
    }
    val sdkByExactName = getSdkByExactName(name)
    if (sdkByExactName != null && projectWslDistr == tryGetWslDistributionForPath(sdkByExactName.homePath)) {
      return sdkByExactName
    }
    return MavenUtil.suggestProjectSdk(project) ?: throw InvalidSdkException(name)
  }

  @JvmStatic
  fun getPropertiesFromMavenOpts(distribution: WSLDistribution): Map<String, String> {
    return parseMavenProperties(MavenWslCache.getInstance().wslEnv(distribution)?.get("MAVEN_OPTS"))
  }

  @JvmStatic
  fun getWslDistribution(project: Project): WSLDistribution {
    val basePath = project.basePath ?: throw IllegalArgumentException("Project $project with null base path")
    return WslPath.getDistributionByWindowsUncPath(basePath)
           ?: throw IllegalArgumentException("Distribution for path $basePath not found, check your WSL installation")
  }

  @JvmStatic
  fun tryGetWslDistribution(project: Project): WSLDistribution? {
    return project.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) }
  }

  @JvmStatic
  fun tryGetWslDistributionForPath(path: String?): WSLDistribution? {
    return path?.let { WslPath.getDistributionByWindowsUncPath(it) }
  }

  /**
   * return file in windows-style ("\\wsl$\distrib-name\home\user\.m2\settings.xml")
   */
  @JvmStatic
  fun WSLDistribution.resolveUserSettingsFile(overriddenUserSettingsFile: String?): File {
    if (isEmptyOrSpaces(overriddenUserSettingsFile)) {
      return File(resolveM2Dir(), SETTINGS_XML)
    }
    else {
      return File(overriddenUserSettingsFile)
    }
  }

  /**
   * return file in windows-style ("\\wsl$\distrib-name\home\user\.m2\settings.xml")
   */
  @JvmStatic
  fun WSLDistribution.resolveGlobalSettingsFile(overriddenMavenHome: String?): File? {
    val directory = this.resolveMavenHomeDirectory(overriddenMavenHome) ?: return null
    return File(File(directory, CONF_DIR), SETTINGS_XML)
  }

  @JvmStatic
  fun WSLDistribution.resolveM2Dir(): File {
    val userHome = MavenWslCache.getInstance().wslEnv(this)?.get("HOME")
    if (userHome.isNullOrBlank()) {
      throw CannotStartServerException(SyncBundle.message("maven.sync.wsl.userhome.cannot.resolve"))
    }
    return this.getWindowsFile(File(userHome, DOT_M2_DIR))
  }

  /**
   * return file in windows-style ("\\wsl$\distrib-name\home\user\.m2\settings.xml")
   */
  @JvmStatic
  fun WSLDistribution.resolveMavenHomeDirectory(overrideMavenHome: String?): File? {
    MavenLog.LOG.debug("resolving maven home on WSL with override = \"${overrideMavenHome}\"")
    if (overrideMavenHome != null) {
      if (overrideMavenHome == MavenServerManager.BUNDLED_MAVEN_3) {
        return MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toFile()
      }
      val home = File(overrideMavenHome)
      if (isValidMavenHome(home)) {
        MavenLog.LOG.debug("resolved maven home as ${overrideMavenHome}")
        return home
      }
      else {
        MavenLog.LOG.debug("Maven home ${overrideMavenHome} on WSL is invalid")
        return null
      }
    }
    val m2home = MavenWslCache.getInstance().wslEnv(this)?.get(ENV_M2_HOME)
    if (m2home != null && !isEmptyOrSpaces(m2home)) {
      val homeFromEnv = this.getWindowsPath(m2home)?.let(::File)
      if (isValidMavenHome(homeFromEnv)) {
        MavenLog.LOG.debug("resolved maven home using \$M2_HOME as ${homeFromEnv}")
        return homeFromEnv
      }
      else {
        MavenLog.LOG.debug("Maven home using \$M2_HOME is invalid")
        return null
      }
    }


    var home = this.getWindowsPath("/usr/share/maven")?.let(::File)
    if (isValidMavenHome(home)) {
      MavenLog.LOG.debug("Maven home found at /usr/share/maven")
      return home
    }

    home = this.getWindowsPath("/usr/share/maven2")?.let(::File)
    if (isValidMavenHome(home)) {
      MavenLog.LOG.debug("Maven home found at /usr/share/maven2")
      return home
    }

    val options = WSLCommandLineOptions()
      .setExecuteCommandInLoginShell(true)
      .setShellPath(this.shellPath)
    val processOutput = this.executeOnWsl(listOf("which", "mvn"), options, 10000, null)
    if (processOutput.exitCode == 0) {
      val path = processOutput.stdout.lines().find { it.isNotEmpty() }?.let(this::resolveSymlink)?.let(this::getWindowsPath)?.let(::File)
      if (path != null) {
        return path
      }

    }
    MavenLog.LOG.debug("mvn not found in \$PATH")

    MavenLog.LOG.debug("Maven home not found on ${this.presentableName}")
    return null
  }

  /**
   * return file in windows style
   */
  @JvmStatic
  fun WSLDistribution.resolveLocalRepository(overriddenLocalRepository: String?,
                                             overriddenMavenHome: String?,
                                             overriddenUserSettingsFile: String?): File {
    if (overriddenLocalRepository != null && !isEmptyOrSpaces(overriddenLocalRepository)) {
      return File(overriddenLocalRepository)
    }
    return doResolveLocalRepository(this.resolveUserSettingsFile(overriddenUserSettingsFile),
                                    this.resolveGlobalSettingsFile(overriddenMavenHome))?.let { this.getWindowsFile(it) }
           ?: File(this.resolveM2Dir(), REPOSITORY_DIR)

  }

  @JvmStatic
  internal fun WSLDistribution.getDefaultMavenDistribution(overriddenMavenHome: String? = null): WslMavenDistribution? {
    val file = this.resolveMavenHomeDirectory(overriddenMavenHome) ?: return null
    val wslFile = this.getWslPath(file.path) ?: return null
    return WslMavenDistribution(this, wslFile, "default")
  }

  @JvmStatic
  fun getJdkPath(wslDistribution: WSLDistribution): String? {
    return MavenWslCache.getInstance().wslEnv(wslDistribution)?.get("JDK_HOME")
  }

  @JvmStatic
  fun WSLDistribution.getWindowsFile(wslFile: File): File {
    return FileUtil.toSystemIndependentName(wslFile.path).let(this::getWindowsPath).let(::File)
  }

  @JvmStatic
  fun WSLDistribution.getWslFile(windowsFile: File): File? {
    return windowsFile.path.let(this::getWslPath)?.let(::File)
  }

  @JvmStatic
  fun <T> resolveWslAware(project: Project?, ordinary: Supplier<T>, wsl: Function<WSLDistribution, T>): T {
    if (project == null && MavenUtil.isMavenUnitTestModeEnabled()) {
      MavenLog.LOG.error("resolveWslAware: Project is null")
    }
    val wslDistribution = project?.let { tryGetWslDistribution(it) } ?: return ordinary.get()
    return wsl.apply(wslDistribution)
  }

  @JvmStatic
  fun getLocalRepo(project: Project?, overriddenLocalRepository: String?, mavenHome: String?,
                   mavenSettingsFile: String?, mavenConfig: MavenConfig?): File {
    var settingPath = mavenSettingsFile;
    if (StringUtil.isEmptyOrSpaces(mavenSettingsFile)) {
      settingPath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_USER_SETTINGS) ?: ""
    }
    return resolveWslAware(project,
                           { resolveLocalRepository(overriddenLocalRepository, mavenHome, settingPath) },
                           { wsl: WSLDistribution -> wsl.resolveLocalRepository(overriddenLocalRepository, mavenHome, settingPath) })
  }

  @JvmStatic
  fun getUserSettings(project: Project?, userSettingsPath: String?, mavenConfig: MavenConfig?): File {
    var settingPath = userSettingsPath;
    if (StringUtil.isEmptyOrSpaces(userSettingsPath)) {
      settingPath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_USER_SETTINGS) ?: ""
    }
    return resolveWslAware(project,
                           { resolveUserSettingsFile(settingPath) },
                           { wsl: WSLDistribution -> wsl.resolveUserSettingsFile(settingPath) })
  }

  @JvmStatic
  fun getGlobalSettings(project: Project?, globalSettingsPath: String?, mavenConfig: MavenConfig?): File? {
    val filePath = mavenConfig?.getFilePath(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS)
    if (filePath != null) return File(filePath)
    return resolveWslAware(project,
                           { resolveGlobalSettingsFile(globalSettingsPath) },
                           { wsl: WSLDistribution -> wsl.resolveGlobalSettingsFile(globalSettingsPath) })
  }

  @JvmStatic
  fun resolveMavenHome(project: Project?, mavenHome: String?) =
    resolveWslAware(project,
                    { resolveMavenHomeDirectory(mavenHome) },
                    { wsl: WSLDistribution -> wsl.resolveMavenHomeDirectory(mavenHome) })

  @JvmStatic
  fun useWslMaven(project: Project): Boolean {
    val projectWslDistr = tryGetWslDistribution(project) ?: return false
    val jdkWslDistr = tryGetWslDistributionForPath(ProjectRootManager.getInstance(project).projectSdk?.homePath) ?: return true
    return jdkWslDistr.id == projectWslDistr.id
  }

  @JvmStatic
  fun sameDistributions(first: WSLDistribution?, second: WSLDistribution?): Boolean {
    return first?.id == second?.id
  }

  @JvmStatic
  fun restartMavenConnectorsIfJdkIncorrect(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          val projectWslDistr = tryGetWslDistribution(project)
          var needReset = false

          MavenServerManager.getInstance().allConnectors.forEach {
            if (it.project == project) {
              val jdkWslDistr = tryGetWslDistributionForPath(it.jdk.homePath)
              if ((projectWslDistr != null && it.supportType != "WSL") || !sameDistributions(projectWslDistr, jdkWslDistr)) {
                needReset = true
                it.shutdown(true)
              }
            }
          }
          if (!needReset) {
            MavenProjectsManager.getInstance(project).embeddersManager.reset()
          }

        }, SyncBundle.message("maven.sync.restarting"), false, project)
    }

  }

  @JvmStatic
  fun checkWslJdkAndShowNotification(project: Project?) {
    val projectWslDistr = tryGetWslDistribution(project!!)
    val sdk = ProjectRootManager.getInstance(project).projectSdk
    if (sdk == null) return;
    val jdkWslDistr = tryGetWslDistributionForPath(sdk.homePath)
    val FIX_STR = "FIX"
    val OPEN_STR = "OPEN"

    if (sameDistributions(projectWslDistr, jdkWslDistr)) return
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
          if (trySetUpExistingJdk(project, projectWslDistr, notification)) return
          ApplicationManager.getApplication().invokeLater {
            findOrDownloadNewJdk(project, projectWslDistr, sdk, notification, this)
          }
        }
      }
    }
    if (projectWslDistr != null && jdkWslDistr == null) {
      Notification(MAVEN_NOTIFICATION_GROUP,
                   MavenProjectBundle.message("wsl.windows.jdk.used.for.wsl"),
                   MavenProjectBundle.message("wsl.windows.jdk.used.for.wsl.descr", OPEN_STR, FIX_STR),
                   NotificationType.WARNING).setListener(listener).notify(project)
    }
    else if (projectWslDistr == null && jdkWslDistr != null) {
      Notification(MAVEN_NOTIFICATION_GROUP,
                   MavenProjectBundle.message("wsl.wsl.jdk.used.for.windows"),
                   MavenProjectBundle.message("wsl.wsl.jdk.used.for.windows.descr", OPEN_STR, FIX_STR),
                   NotificationType.WARNING).setListener(listener).notify(project)
    }
    else if (projectWslDistr != null && jdkWslDistr != null) {
      Notification(MAVEN_NOTIFICATION_GROUP,
                   MavenProjectBundle.message("wsl.different.wsl.jdk.used"),
                   MavenProjectBundle.message("wsl.different.wsl.jdk.used.descr", OPEN_STR, FIX_STR),
                   NotificationType.WARNING).setListener(listener).notify(project)
    }
  }

  private fun trySetUpExistingJdk(project: Project, projectWslDistr: WSLDistribution?, notification: Notification): Boolean {
    val sdk = service<ProjectJdkTable>().allJdks.filter {
      sameDistributions(projectWslDistr, it.homePath?.let(WslPath::getDistributionByWindowsUncPath))
    }.maxWithOrNull(compareBy(VersionComparatorUtil.COMPARATOR) { it.versionString })
    if (sdk == null) return false;
    WriteAction.runAndWait<RuntimeException> {
      ProjectRootManagerEx.getInstance(project).projectSdk = sdk
      notification.hideBalloon()
    }
    return true
  }

  private fun findOrDownloadNewJdk(project: Project,
                                   projectWslDistr: WSLDistribution?,
                                   sdk: Sdk,
                                   notification: Notification,
                                   listener: NotificationListener) {
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
          val homeDir = installer.defaultInstallDir(model[0], projectWslDistr)
          val request = installer.prepareJdkInstallation(model[0], homeDir)
          installer.installJdk(request, indicator, project)
          notification.hideBalloon()
        }
      }
    }
    ProgressManager.getInstance().run(jdkTask)
  }
}


class MavenWslCache {

  private val myEnvCache: MutableMap<String, Map<String, String>> = HashMap()

  fun wslEnv(distribution: WSLDistribution): Map<String, String>? {
    var result = myEnvCache[distribution.msId];
    if (result != null) return result
    for(i in 1..2){
      result = distribution.environment
      if(result!=null) {
        myEnvCache[distribution.msId] = result
        return result
      }
    }
    return null;
  }

  fun clearCache() {
    myEnvCache.clear()
  }

  companion object {
    @JvmStatic
    fun getInstance(): MavenWslCache {
      return ApplicationManager.getApplication().getService(MavenWslCache::class.java)
    }
  }
}
