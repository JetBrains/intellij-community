package com.intellij.python.pyproject.model.spi

typealias WorkspaceMember = ProjectName
typealias WorkspaceName = ProjectName

data class ProjectStructureInfo(
  /**
   * [project -> its dependencies]
   */
  val dependencies: Map<ProjectName, Set<ProjectName>>,
  /**
   * Projects that are memvbers of workspace point to the root of workspace
   */
  val membersToWorkspace: Map<WorkspaceMember, WorkspaceName>,
)
