package com.intellij.ide.starter.models

import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.project.ProjectInfo
import com.intellij.ide.starter.project.ProjectInfoSpec

data class TestCase(
  val ideInfo: IdeInfo,
  val projectInfo: ProjectInfoSpec? = null,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val vmOptionsFix: VMOptions.() -> VMOptions = { this },
  val useInMemoryFileSystem: Boolean = false
) {
  fun withProject(projectInfo: ProjectInfoSpec): TestCase = copy(projectInfo = projectInfo)

  fun withCommands(commands: Iterable<MarshallableCommand> = this.commands): TestCase = copy(commands = commands.toList())

  /** On each test run the project will be unpacked again.
   * This guarantees that there is not side effects from previous test runs
   **/
  fun markNotReusable(): TestCase = copy(projectInfo = (projectInfo as ProjectInfo).copy(isReusable = false))

  /**
   * [buildNumber] - EAP build number to download
   * E.g: "222.3244.1"
   * If empty - the latest EAP will be downloaded.
   * [Downloads for IDEA Ultimate](https://www.jetbrains.com/idea/download/other.html)
   **/
  fun useEAP(buildNumber: String = ""): TestCase {
    return copy(ideInfo = ideInfo.copy(buildType = BuildType.EAP.type, buildNumber = buildNumber))
  }

  /**
   * [version] - Release version to download
   * E.g: "2022.1.2"
   * If empty - the latest release will be downloaded.
   * [Downloads for IDEA Ultimate](https://www.jetbrains.com/idea/download/other.html)
   **/
  fun useRelease(version: String = ""): TestCase {
    return copy(ideInfo = ideInfo.copy(buildType = BuildType.RELEASE.type, version = version))
  }
}
