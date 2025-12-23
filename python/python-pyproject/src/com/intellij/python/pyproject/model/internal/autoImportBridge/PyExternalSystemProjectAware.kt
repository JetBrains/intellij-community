package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.project.stateStore
import com.intellij.psi.PsiDocumentManager
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.internal.PyProjectTomlBundle
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemNoTomlContent
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemWithTomlContent
import com.intellij.python.pyproject.model.internal.workspaceBridge.rebuildProjectModel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

internal class PyExternalSystemProjectAware private constructor(
  private val project: Project,
  private val projectRootDir: Path,
) : ExternalSystemProjectAware {
  override val projectId: ExternalSystemProjectId = ExternalSystemProjectId(SYSTEM_ID, projectRootDir.pathString)


  @get:RequiresBackgroundThread
  override val settingsFiles: Set<String>
    get() = runBlockingMaybeCancellable {
      // We do not need file content: only names here.
      val fsInfo = walkFileSystemNoTomlContent(projectRootDir)
      return@runBlockingMaybeCancellable fsInfo.rawTomlFiles.map { it.pathString }.toSet()
    }

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_AWARE_TOPIC, listener)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    reloadProjectImpl()
  }

  internal fun reloadProjectImpl() {
    project.service<PyExternalSystemProjectAwareService>().scope.launchTracked {
      writeAction {
        // We might get stale files otherwise
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
      project.messageBus.syncAndPreloadPublisher(PROJECT_AWARE_TOPIC).apply {
        try {
          val files = walkFileSystemWithTomlContent(projectRootDir)
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
    /**
     * [project] can't be default, be sure to check it
     */
    internal suspend fun create(project: Project): PyExternalSystemProjectAware {
      assert(!project.isDefault) { "Default project not supported" }
      val baseDir = withContext(Dispatchers.IO) {
        // guessPath doesn't work: it returns first module path
        project.stateStore.projectBasePath
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
internal val MODEL_REBUILD: Topic<ModelRebuiltListener> = Topic(ModelRebuiltListener::class.java, Topic.BroadcastDirection.NONE)

@Service(Service.Level.PROJECT)
private class PyExternalSystemProjectAwareService(val scope: CoroutineScope)