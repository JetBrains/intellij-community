package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectReloadContext
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.project.stateStore
import com.intellij.python.pyproject.model.internal.PY_PROJECT_SYSTEM_ID
import com.intellij.python.pyproject.model.internal.PyProjectScopeService
import com.intellij.python.pyproject.model.internal.notifyModelRebuilt
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemNoTomlContent
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemWithTomlContent
import com.intellij.python.pyproject.model.internal.workspaceBridge.rebuildProjectModel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@VisibleForTesting
class PyExternalSystemProjectAware private constructor(
  private val project: Project,
  private val projectRootDir: Path,
) : ExternalSystemProjectAware {
  override val projectId: ExternalSystemProjectId = ExternalSystemProjectId(PY_PROJECT_SYSTEM_ID, projectRootDir.pathString)


  @get:RequiresBackgroundThread
  override val settingsFiles: Set<String>
    get() {
      if (EDT.isCurrentThreadEdt() && ApplicationManager.getApplication().isUnitTestMode) {
        // Some tests are broken and access it from EDT.
        // Since `@RequiresBackgroundThread` doesn't work for Kotlin, we can't check it in advance.
        // This part will be rewritten soon anyway, so for now enjoy workaround
        log.warn("Access from EDT, settingsFiles are empty")
        return emptySet()
      }
      return runBlockingMaybeCancellable {
        // We do not need file content: only names here.
        val fsInfo = walkFileSystemNoTomlContent(projectRootDir).getOr {
          // Dir can't be accessed
          log.trace(it.error)
          return@runBlockingMaybeCancellable emptySet()
        }
        return@runBlockingMaybeCancellable fsInfo.rawTomlFiles.map { it.pathString }.toSet()
      }
    }

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_AWARE_TOPIC, listener)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    project.service<PyProjectScopeService>().scope.launchTracked {
      reloadProjectImpl()
    }
  }

  @ApiStatus.Internal
  @VisibleForTesting
  suspend fun reloadProjectImpl() {
    writeAction {
      // We might get stale files otherwise
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    project.messageBus.syncAndPreloadPublisher(PROJECT_AWARE_TOPIC).apply {
      try {
        this.onProjectReloadStart()
        val files = walkFileSystemWithTomlContent(projectRootDir).getOr {
          if (log.isTraceEnabled) {
            log.warn("Can't access $projectRootDir", it.error)
          }
          this.onProjectReloadFinish(ExternalSystemRefreshStatus.FAILURE)
          return
        }
        rebuildProjectModel(project, files)
        this.onProjectReloadFinish(ExternalSystemRefreshStatus.SUCCESS)
        // Even though we have no entities, we still "rebuilt" the model, time to configure SDK
        notifyModelRebuilt(project)
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


  companion object {
    /**
     * [project] can't be default, be sure to check it
     */
    @ApiStatus.Internal
    @VisibleForTesting
    fun create(project: Project): PyExternalSystemProjectAware {
      assert(!project.isDefault) { "Default project not supported" }
      // guessPath doesn't work: it returns first module path
      val baseDir = project.stateStore.projectBasePath
      return PyExternalSystemProjectAware(project, baseDir)
    }
  }
}


@Topic.ProjectLevel
private val PROJECT_AWARE_TOPIC: Topic<ExternalSystemProjectListener> =
  Topic(ExternalSystemProjectListener::class.java, Topic.BroadcastDirection.NONE)


private val log = fileLogger()