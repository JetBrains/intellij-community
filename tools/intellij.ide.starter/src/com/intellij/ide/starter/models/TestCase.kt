package com.intellij.ide.starter.models

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

  fun markNotReusable(): TestCase = markReusable(false)

  fun markReusable(isReusable: Boolean = true) = copy(projectInfo = (projectInfo as ProjectInfo).copy(isReusable = isReusable))

}
