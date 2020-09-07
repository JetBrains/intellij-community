package com.intellij.space.vcs.share

import circlet.client.api.PR_Project
import circlet.client.api.Projects
import circlet.client.pr
import circlet.common.permissions.ProjectRight
import circlet.common.permissions.VcsAdmin
import com.intellij.space.components.space
import circlet.platform.api.batchAll
import circlet.platform.api.map
import circlet.platform.client.ConnectionStatus
import circlet.platform.client.resolve
import com.intellij.space.messages.SpaceBundle
import kotlinx.coroutines.CancellationException
import libraries.coroutines.extra.LifetimeSource
import org.jetbrains.annotations.Nls
import runtime.reactive.MutableProperty
import runtime.reactive.awaitFirst
import runtime.reactive.filter
import runtime.reactive.mapInit

class SpaceShareProjectVM(val lifetime: LifetimeSource) {

  @Suppress("RemoveExplicitTypeArguments")
  internal val projectsListState: MutableProperty<ProjectListState> = lifetime.mapInit<ProjectListState>(ProjectListState.Loading) {
    val ws = space.workspace.value ?: return@mapInit ProjectListState.Error()
    val client = ws.client
    client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)

    try {
      val projectService: Projects = client.pr
      // projects in which there is the right to create new repositories
      val projects = projectService.projectsWithRight(batchAll, VcsAdmin.code, null, null)
        .map { it.resolve() }
        .data
      ProjectListState.Projects(projects)
    }
    catch (th: CancellationException) {
      throw th
    }
    catch (e: Exception) {
      ProjectListState.Error()
    }
  }

  sealed class ProjectListState {
    object Loading : ProjectListState()

    class Error(@Nls val error: String = SpaceBundle.message("share.project.unable.to.load.projects")) : ProjectListState()

    class Projects(val projects: List<PR_Project>) : ProjectListState()
  }
}
