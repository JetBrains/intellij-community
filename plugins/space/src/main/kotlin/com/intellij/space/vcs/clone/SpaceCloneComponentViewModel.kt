// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.clone

import circlet.client.api.*
import circlet.client.api.impl.vcsPasswords
import circlet.client.pr
import circlet.client.repoService
import circlet.client.ssh
import circlet.client.star
import circlet.platform.api.Ref
import circlet.platform.client.XPagedListOnFlux
import circlet.platform.client.resolve
import circlet.platform.client.resolveRefsOrFetch
import circlet.platform.client.xTransformedPagedListOnFlux
import circlet.workspaces.Workspace
import com.intellij.space.settings.CloneType
import com.intellij.space.settings.CloneType.HTTPS
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.vcs.SpaceHttpPasswordState
import com.intellij.space.vcs.SpaceKeysState
import com.intellij.util.ui.cloneDialog.SearchableListItem
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.UiDispatch
import runtime.dispatchInterval
import runtime.reactive.MutableProperty
import runtime.reactive.Property
import runtime.reactive.mutableProperty
import runtime.reactive.property.mapInit
import runtime.utils.mapToSet

internal class SpaceCloneComponentViewModel(
  override val lifetime: Lifetime,
  workspace: Workspace
) : Lifetimed {
  private val projectService: Projects = workspace.client.pr
  private val repositoryService: RepositoryService = workspace.client.repoService
  private val starService: Star = workspace.client.star
  private val ssh: SshKeys = workspace.client.ssh

  val isLoading: MutableProperty<Boolean> = mutableProperty(false)

  val me: MutableProperty<TD_MemberProfile> = workspace.me

  val repos: XPagedListOnFlux<List<SpaceCloneListItem>> = xTransformedPagedListOnFlux(
    client = workspace.client,
    batchSize = 10,
    keyFn = { it.id },
    result = { allProjectRefs ->
      val allProjectsWithRepos = workspace.client.arena.resolveRefsOrFetch {
        allProjectRefs.map { it to it.extensionRef(ProjectReposRecord::class) }
      }
      val starredProjectIds = starService.starredProjects().mapToSet(Ref<PR_Project>::id)

      allProjectsWithRepos.map { (projectRef, reposRef) ->
        val project = projectRef.resolve()
        val repos = reposRef.resolve().repos
        val isStarred = projectRef.id in starredProjectIds
        repos.map { repo ->
          val detailsProperty = mutableProperty<RepoDetails?>(null)
          val item = SpaceCloneListItem(project, isStarred, repo, detailsProperty)
          item.visible.forEach(lifetime) { visible ->
            launch(lifetime, Ui) {
              if (visible) {
                detailsProperty.value = repositoryService.repositoryDetails(project.key, repo.name)
              }
            }
          }
          item
        }
      }
    }
  ) { batch ->
    projectService.projectsBatch(batch, "", "")
  }

  val cloneType: MutableProperty<CloneType> = mutableProperty(SpaceSettings.getInstance().cloneType)

  val selectedCloneListItem: MutableProperty<SpaceCloneListItem?> = mutableProperty(null)

  val spaceHttpPasswordState: MutableProperty<SpaceHttpPasswordState> = lifetime.mapInit(cloneType, SpaceHttpPasswordState.NotChecked) { cloneType ->
    if (cloneType == CloneType.SSH) return@mapInit SpaceHttpPasswordState.NotChecked

    workspace.client.api.vcsPasswords().getVcsPassword(me.value.identifier).let {
      if (it == null) SpaceHttpPasswordState.NotSet else SpaceHttpPasswordState.Set(it)
    }
  } as MutableProperty<SpaceHttpPasswordState>

  val circletKeysState: MutableProperty<SpaceKeysState> = run {
    val property: MutableProperty<SpaceKeysState> = mutableProperty(SpaceKeysState.NotChecked)
    UiDispatch.dispatchInterval(1000, lifetime) {
      launch(lifetime, Ui) {
        property.value = loadSshState(cloneType.value)
      }
    }
    property
  }

  private suspend fun loadSshState(cloneType: CloneType): SpaceKeysState {
    if (cloneType == HTTPS) return SpaceKeysState.NotChecked

    return ssh.sshKeys(me.value.identifier).let {
      if (it.isNullOrEmpty()) SpaceKeysState.NotSet else SpaceKeysState.Set(it)
    }
  }

  suspend fun loadDetails(spaceCloneListItem: SpaceCloneListItem): RepoDetails {
    return repositoryService.repositoryDetails(spaceCloneListItem.project.key, spaceCloneListItem.repoInfo.name)
  }

  val readyToClone: Property<Boolean> = lifetime.mapInit(selectedCloneListItem, spaceHttpPasswordState, circletKeysState, false) { repo, http, ssh ->
    repo != null && (http is SpaceHttpPasswordState.Set || ssh is SpaceKeysState.Set)
  }
}

data class SpaceCloneListItem(
  val project: PR_Project,
  val starred: Boolean,
  val repoInfo: PR_RepositoryInfo,
  val repoDetails: Property<RepoDetails?>
) : SearchableListItem, Comparable<SpaceCloneListItem> {

  val visible = mutableProperty(false)

  override val stringToSearch: String
    get() = repoInfo.name

  override fun compareTo(other: SpaceCloneListItem): Int {
    if (starred != other.starred) {
      return other.starred.compareTo(starred)
    }
    if (project.name != other.project.name) {
      return project.name.compareTo(other.project.name)
    }
    return repoInfo.name.compareTo(other.repoInfo.name)
  }
}
