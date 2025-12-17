package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.internal.PyProjectTomlBundle
import com.intellij.python.pyproject.model.internal.workspaceBridge.rebuildProjectModel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

internal class PyExternalSystemProjectAware private constructor(
  private val project: Project,
  projectRootDir: Path,
) : ExternalSystemProjectAware {
  override val projectId: ExternalSystemProjectId = ExternalSystemProjectId(SYSTEM_ID, projectRootDir.pathString)

  private val fsInfo = FsInfoStorage(projectRootDir)

  @get:RequiresBackgroundThread
  override val settingsFiles: Set<String>
    get() = runBlockingMaybeCancellable {
      val fsInfo = fsInfo.getFsInfo(forceRefresh = true)
      return@runBlockingMaybeCancellable fsInfo.tomlFiles.keys.map { it.pathString }.toSet()
    }

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_AWARE_TOPIC, listener)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    project.service<PyProjectAutoImportService>().scope.launchTracked {
      project.messageBus.syncAndPreloadPublisher(PROJECT_AWARE_TOPIC).apply {
        try {
          val files = fsInfo.getFsInfo(forceRefresh = context.hasUndefinedModifications)
          this.onProjectReloadStart()
          rebuildProjectModel(project, files)
          this.onProjectReloadFinish(ExternalSystemRefreshStatus.SUCCESS)
          // Even though we have no entities, we still "rebuilt" the model
          withContext(Dispatchers.Default) {
            project.messageBus.syncPublisher(MODEL_REBUILD).modelRebuilt(project)
          }
        }
        catch (e: CancellationException) {
          this.onProjectReloadFinish(ExternalSystemRefreshStatus.CANCEL)
          throw e
        }
        catch (e: Exception) {
          this.onProjectReloadFinish(ExternalSystemRefreshStatus.FAILURE)
          throw e
        }
      }
    }
  }


  companion object {
    internal suspend fun create(project: Project): PyExternalSystemProjectAware {
      val baseDir = withContext(Dispatchers.IO) {
        project.guessProjectDir()?.toNioPath() ?: error("Project $project has no base dir")
      }
      return PyExternalSystemProjectAware(project, baseDir)
    }

    @Suppress("DialogTitleCapitalization") //pyproject.toml can't be capitalized
    private val SYSTEM_ID = ProjectSystemId("pyproject.toml", PyProjectTomlBundle.message("intellij.python.pyproject.system.name"))
  }
}


@Topic.ProjectLevel
private val PROJECT_AWARE_TOPIC: Topic<ExternalSystemProjectListener> =
  Topic(ExternalSystemProjectListener::class.java, Topic.BroadcastDirection.NONE)

@Topic.ProjectLevel
private val MODEL_REBUILD: Topic<ModelRebuiltListener> = Topic(ModelRebuiltListener::class.java, Topic.BroadcastDirection.NONE)