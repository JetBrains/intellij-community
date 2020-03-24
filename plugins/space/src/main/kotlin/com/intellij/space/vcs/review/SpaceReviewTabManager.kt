package com.intellij.space.vcs.review

import circlet.client.api.ProjectKey
import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.space.components.space
import com.intellij.space.vcs.Context
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import icons.SpaceIcons
import libraries.coroutines.extra.LifetimeSource
import runtime.reactive.Property
import runtime.reactive.mapInit
import javax.swing.JPanel
import kotlin.collections.set

@Service
internal class SpaceCodeReviewTabManager(private val project: Project) {

  private lateinit var myReviewTabContentManager: SpaceCodeReviewTabContentManager

  companion object {
    fun getInstance(project: Project): SpaceCodeReviewTabManager = project.service()
  }

  internal fun showReviews(contentManager: ContentManager) {
    myReviewTabContentManager = SpaceCodeReviewTabContentManager(project, contentManager)
  }
}

internal class SpaceCodeReviewTabContentManager(private val project: Project, private val cm: ContentManager) {
  private val lifetime: LifetimeSource = LifetimeSource()
  private val workspace: Property<Workspace?> = space.workspace
  private val context: Property<Context> = SpaceProjectContext.getInstance(project).context

  private val contents: Property<MutableMap<String, Content>> =
    lifetime.mapInit(workspace, context, HashMap()) { ws, context ->
      if (ws == null) {
        return@mapInit HashMap<String, Content>()
      }

      if (!context.isAssociatedWithSpaceRepository) {
        return@mapInit HashMap<String, Content>()
      }

      val result = HashMap<String, Content>()
      context.reposInProject.forEach {
        val (prKey, spaceProject) = it.key
        val repoInfo = it.value
        val key = prKey.key
        val content = createContent(project, prKey, repoInfo)
        result[key] = content
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
          println("content added ${it}")
          cm.addContent(next[it]!!)
          cm.setSelectedContent(next[it]!!)
        }
    }
  }

  private fun createContent(project: Project,
                            projectKey: ProjectKey,
                            repoInfo: Set<SpaceRepoInfo>): Content {
    val lifeTime = LifetimeSource()
    val factory = ContentFactory.SERVICE.getInstance()

    return factory.createContent(JPanel(null), projectKey.key, false).apply {
      val disposable = Disposable {
        lifeTime.terminate()
      }
      isCloseable = false
      setDisposer(disposable)
      icon = SpaceIcons.Main

      component = ReviewLoginComponent(lifetime, project, projectKey, repoInfo).view
      description = projectKey.key
    }
  }
}
