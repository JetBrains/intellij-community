package com.intellij.ide.starter.models

import com.intellij.ide.starter.community.PublicIdeDownloader
import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.StandardInstaller
import com.intellij.ide.starter.project.GitProjectInfo
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand

data class TestCase<T : ProjectInfoSpec>(
  val ideInfo: IdeInfo,
  val projectInfo: T,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val useInMemoryFileSystem: Boolean = false
) {

  fun withProject(projectInfo: T): TestCase<T> = copy(projectInfo = projectInfo)

  fun withCommands(commands: Iterable<MarshallableCommand> = this.commands): TestCase<T> = copy(commands = commands.toList())

  /**
   * You may consider using this method with [IdeProductProvider]
   */
  fun onIDE(ideInfo: IdeInfo): TestCase<T> = copy(ideInfo = ideInfo)

  /**
   * reusable = false - On each test run the project will be unpacked again.
   * This guarantees that there is not side effects from previous test runs
   **/
  fun markReusable(reusable: Boolean = true): TestCase<T> = when (projectInfo) {
    is RemoteArchiveProjectInfo -> copy(projectInfo = projectInfo.copy(isReusable = reusable) as T)
    is GitProjectInfo -> copy(projectInfo = projectInfo.copy(isReusable = reusable) as T)
    is LocalProjectInfo -> copy(projectInfo = projectInfo.copy(isReusable = reusable) as T)
    else -> {
      throw IllegalStateException("Can't mark not reusable for ${projectInfo.javaClass}")
    }
  }

  private fun copyWithPublicIdeDownloaderAndUpdatedInfo(
    buildType: BuildType? = null,
    buildNumber: String? = null,
    version: String? = null
  ): TestCase<T> {
    return copy(
      ideInfo = ideInfo.copy(
        buildType = buildType?.type ?: "",
        buildNumber = buildNumber ?: "",
        version = version ?: ""
      ).run { this.copy(getInstaller = { StandardInstaller(PublicIdeDownloader()) }) }
    )
  }

  fun useRC(): TestCase<T> = copyWithPublicIdeDownloaderAndUpdatedInfo(buildType = BuildType.RC)

  fun useEAP(): TestCase<T> = copyWithPublicIdeDownloaderAndUpdatedInfo(buildType = BuildType.EAP)

  /** E.g: "222.3244.1" */
  fun useEAP(buildNumber: String = ""): TestCase<T> = copyWithPublicIdeDownloaderAndUpdatedInfo(BuildType.EAP,
                                                                                                        buildNumber = buildNumber)

  fun useRelease(): TestCase<T> = copyWithPublicIdeDownloaderAndUpdatedInfo(buildType = BuildType.RELEASE)

  /** E.g: "2022.1.2" */
  fun useRelease(version: String = ""): TestCase<T> = copyWithPublicIdeDownloaderAndUpdatedInfo(BuildType.RELEASE, version = version)

  /** If you are unsure about the need to use this method,
   *  it is recommended to first familiarize yourself with the functionality of
   *  [useEAP] and make sure it does not meet your requirements.
   *  E.g: "222.3244.1" */
  fun withBuildNumber(buildNumber: String): TestCase<T> = copyWithPublicIdeDownloaderAndUpdatedInfo(buildNumber = buildNumber)

  /** If you are unsure about the need to use this method,
   *  it is recommended to first familiarize yourself with the functionality of
   *  [useRelease] and make sure it does not meet your requirements
   *  E.g: "2022.1.2" */
  fun withVersion(version: String): TestCase<T> = copyWithPublicIdeDownloaderAndUpdatedInfo(version = version)
}
