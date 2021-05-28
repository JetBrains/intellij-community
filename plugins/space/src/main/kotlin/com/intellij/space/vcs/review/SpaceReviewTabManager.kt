// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.space.vcs.Context
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import icons.SpaceIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.LifetimeSource
import runtime.reactive.LoadingProperty
import runtime.reactive.LoadingValue
import runtime.reactive.Property
import runtime.reactive.property.mapInit

@Service
internal class SpaceCodeReviewTabManager(private val project: Project) : LifetimedDisposable by LifetimedDisposableImpl() {

  private var myReviewTabContentManager: SpaceCodeReviewTabContentManager? = null

  init {
    project.service<SpaceProjectContext>().probablyContainsSpaceRepo.forEach(lifetime) {
      val toolWindow: ToolWindow = ToolWindowManager
                                     .getInstance(project)
                                     .getToolWindow(SpaceReviewToolWindowFactory.ID) ?: return@forEach
      if (it && !toolWindow.isAvailable) {
        toolWindow.isShowStripeButton = true
      }
      toolWindow.isAvailable = it
    }
  }

  companion object {
    fun getInstance(project: Project): SpaceCodeReviewTabManager = project.service()
  }

  internal fun showReviews(contentManager: ContentManager) {
    if (myReviewTabContentManager == null) {
      myReviewTabContentManager = SpaceCodeReviewTabContentManager(project, contentManager, lifetime)
    }
  }
}

internal class SpaceCodeReviewTabContentManager(private val project: Project,
                                                private val contentManager: ContentManager,
                                                lifetime: Lifetime) {
  private val context: LoadingProperty<Context> = SpaceProjectContext.getInstance(project).context

  private val contents: Property<MutableMap<SpaceProjectInfo, Content>> = lifetime.mapInit(context, mutableMapOf()) { loadingContext ->
    if (loadingContext !is LoadingValue.Loaded || !loadingContext.value.isAssociatedWithSpaceRepository) {
      return@mapInit mutableMapOf<SpaceProjectInfo, Content>()
    }
    val context = loadingContext.value
    val result = HashMap<SpaceProjectInfo, Content>()
    context.reposInProject.forEach {
      val content = createContent(project, it.key, it.value)
      result[it.key] = content
    }
    result
  }

  init {
    contents.forEachWithPrevious(lifetime) { prev: MutableMap<SpaceProjectInfo, Content>?, next: MutableMap<SpaceProjectInfo, Content> ->
      val previous = prev ?: emptyMap()
      if (previous.isEmpty()) {
        contentManager.removeAllContents(true)
      }
      previous.keys
        .filter { key -> !next.keys.contains(key) }
        .forEach {
          val content = previous[it]!!
          contentManager.removeContent(content, true)
        }

      next.keys.filter { key -> !previous.contains(key) }
        .forEach {
          val content = next[it]!!
          contentManager.addContent(content)
          contentManager.setSelectedContent(content)
        }

      if (next.isEmpty()) {
        val loginContent = createEmptyContent(project)
        contentManager.addContent(loginContent)
        contentManager.setSelectedContent(loginContent)
      }
    }
  }

  private fun createEmptyContent(project: Project) = createDisposableContent { content, _, contentLifetime ->
    content.component = SpaceReviewToolwindowEmptyComponent(project, contentLifetime)
  }

  private fun createContent(
    project: Project,
    spaceProjectInfo: SpaceProjectInfo,
    projectRepos: Set<SpaceRepoInfo>
  ): Content = createDisposableContent { content, disposable, contentLifetime ->
    content.displayName = spaceProjectInfo.project.name // NON-NLS
    content.isCloseable = false
    content.icon = SpaceIcons.Main
    val workspace = SpaceWorkspaceComponent.getInstance().workspace.value!!
    content.component = SpaceReviewToolwindowTabComponent(disposable, contentLifetime, project, workspace, spaceProjectInfo, projectRepos)
    content.description = spaceProjectInfo.key.key // NON-NLS
  }

  private fun createDisposableContent(modifier: (Content, Disposable, Lifetime) -> Unit): Content {
    val contentLifetime = LifetimeSource()
    val factory = ContentFactory.SERVICE.getInstance()
    return factory.createContent(null, null, false).apply {
      val disposable = Disposable {
        contentLifetime.terminate()
      }
      setDisposer(disposable)
      modifier(this, disposable, contentLifetime)
    }
  }
}
