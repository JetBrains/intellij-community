package com.intellij.python.pyproject.model.spi

typealias WorkspaceMember = ProjectName
typealias WorkspaceName = ProjectName

data class ProjectStructureInfo(
  /**
   * [project -> its dependencies]
   */
  val dependencies: ProjectDependencies,
  /**
   * Projects that are members of workspace point to the root of workspace
   */
  val membersToWorkspace: Map<WorkspaceMember, WorkspaceName>,
)
