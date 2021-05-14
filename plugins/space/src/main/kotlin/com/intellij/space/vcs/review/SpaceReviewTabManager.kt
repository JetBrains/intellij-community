// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.vcs.Context
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import icons.SpaceIcons
import libraries.coroutines.extra.LifetimeSource
import runtime.reactive.Property
import runtime.reactive.property.mapInit

@Service
internal class SpaceCodeReviewTabManager(private val project: Project) {

  private var myReviewTabContentManager: SpaceCodeReviewTabContentManager? = null

  companion object {
    fun getInstance(project: Project): SpaceCodeReviewTabManager = project.service()
  }

  internal fun showReviews(contentManager: ContentManager) {
    if (myReviewTabContentManager == null) {
      myReviewTabContentManager = SpaceCodeReviewTabContentManager(project, contentManager)
    }
  }
}

internal class SpaceCodeReviewTabContentManager(private val project: Project, private val cm: ContentManager) {
  private val lifetime: LifetimeSource = LifetimeSource()
  private val workspace: Property<Workspace?> = SpaceWorkspaceComponent.getInstance().workspace
  private val context: Property<Context> = SpaceProjectContext.getInstance(project).context

  private val contents: Property<MutableMap<SpaceProjectInfo, Content>> =
    lifetime.mapInit(workspace, context, mutableMapOf()) { ws, context ->
      if (ws == null) {
        return@mapInit mutableMapOf<SpaceProjectInfo, Content>()
      }

      if (!context.isAssociatedWithSpaceRepository) {
        return@mapInit mutableMapOf<SpaceProjectInfo, Content>()
      }

      val result = HashMap<SpaceProjectInfo, Content>()
      context.reposInProject.forEach {
        val content = createContent(project, it.key, it.value)
        result[it.key] = content
      }
      result
    }

  init {
    contents.forEachWithPrevious(lifetime) { prev, next ->
      prev?.keys
        ?.filter { key -> !next.keys.contains(key) }.orEmpty()
        .forEach {
          cm.removeContent(prev?.get(it)!!, true)
        }

      next.keys.filter { key -> !(prev?.contains(key) ?: false) }
        .forEach {
          cm.addContent(next[it]!!)
          cm.setSelectedContent(next[it]!!)
        }
    }
  }

  private fun createContent(project: Project,
                            spaceProjectInfo: SpaceProjectInfo,
                            projectRepos: Set<SpaceRepoInfo>): Content {
    val contentLifetime = LifetimeSource()
    val factory = ContentFactory.SERVICE.getInstance()

    return factory.createContent(null, spaceProjectInfo.project.name, false).apply { // NON-NLS
      val disposable = Disposable {
        contentLifetime.terminate()
      }
      isCloseable = false
      setDisposer(disposable)
      icon = SpaceIcons.Main

      component = ReviewLoginComponent(disposable, contentLifetime, project, spaceProjectInfo, projectRepos).view
      description = spaceProjectInfo.key.key // NON-NLS
    }
  }
}
