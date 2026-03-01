package com.intellij.lambda.testFramework.junit

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.starter.coroutine.CommonScope.testSuiteSupervisorScope
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.lambda.testFramework.testApi.waitForNoProjects
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * JUnit 5 extension that handles project preparation and reset for lambda tests.
 *
 * This extension:
 * - Reads the [WithProject] annotation from test method or class
 * - Closes all opened projects before each test
 * - Prepares a fresh copy of the project using the specified [ProjectInfoSpec]
 * - Cleans up the prepared project after each test
 *
 * The project is automatically opened on the backend/monolith.
 * Tests can access the opened project using `getProject()`.
 *
 * Usage:
 * ```kotlin
 * object TestApp : EmbeddedProjectInfo("TestApp")
 *
 * @WithProject(TestApp::class)
 * class MyTest {
 *   @Test
 *   fun myTest(ide: IdeWithLambda) = timeoutRunBlocking {
 *     ide.runInBackend("use project") {
 *       val project = getProject()
 *       // project is already opened and ready
 *     }
 *   }
 * }
 * ```
 */
class ProjectResetCallback : BeforeEachCallback, AfterEachCallback {

  companion object {
    private val LOG = thisLogger()
    private const val PREPARED_PROJECT_PATH_KEY = "preparedProjectPath"

    private val ExtensionContext.store: ExtensionContext.Store
      get() = getStore(ExtensionContext.Namespace.create(ProjectResetCallback::class.java))
  }

  private fun readableTestName(context: ExtensionContext): String =
    context.requiredTestClass.name + "." + context.requiredTestMethod.name + context.displayName + "[${IdeInstance.currentIdeMode}]"

  override fun beforeEach(context: ExtensionContext) {
    val withProject = findWithProjectAnnotation(context)
    if (withProject == null) {
      LOG.info("No @WithProject annotation found, skipping project preparation for ${readableTestName(context)}")
      return
    }

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(testSuiteSupervisorScope.coroutineContext) {
      runLogged("Project reset for ${readableTestName(context)}", 60.seconds) {
        closeAllOpenedProjects()

        // Instantiate the ProjectInfoSpec and prepare the project directory
        val projectInfo = withProject.project.objectInstance ?: error("ProjectInfoSpec class ${withProject.project.simpleName} does not have an object instance")
        val projectPath = projectInfo.downloadAndUnpackProject()
                          ?: error("Failed to prepare project: ${projectInfo.getDescription()}")
        context.store.put(PREPARED_PROJECT_PATH_KEY, projectPath)

        openPreparedProject(projectPath)
      }
    }
  }

  override fun afterEach(context: ExtensionContext) {
    try {
      val withProject = findWithProjectAnnotation(context)
      if (withProject != null && IdeInstance.isStarted()) {
        @Suppress("RAW_RUN_BLOCKING")
        runBlocking(testSuiteSupervisorScope.coroutineContext) {
          runLogged("Project cleanup for ${readableTestName(context)}", 30.seconds) {
            closeAllOpenedProjects()
          }
        }
      }
    }
    finally {
      val projectPath = context.store.remove(PREPARED_PROJECT_PATH_KEY, Path::class.java)
      if (projectPath != null) {
        catchAll("Cleaned up project directory: $projectPath") {
          projectPath.deleteRecursively()
        }
      }
    }
  }

  private fun findWithProjectAnnotation(context: ExtensionContext): WithProject? {
    val methodAnnotation = context.requiredTestMethod.getAnnotation(WithProject::class.java)
    if (methodAnnotation != null) {
      return methodAnnotation
    }

    return context.requiredTestClass.getAnnotation(WithProject::class.java)
  }

  private suspend fun closeAllOpenedProjects() {
    IdeInstance.ide.runInBackend("Close all opened projects", globalTestScope = true) {
      withContext(Dispatchers.EDT + NonCancellable) {
        writeIntentReadAction {
          ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(checkCanClose = false)
        }
      }
    }
    IdeInstance.ide.runInFrontend("Wait for no projects to be opened", globalTestScope = true) {
      waitForNoProjects(timeout = 10.seconds)
    }
  }

  private suspend fun openPreparedProject(projectPath: Path) {
    val ide = IdeInstance.ide
    val pathString = projectPath.toString()

    ide.runInBackend("Open project at $projectPath", parameters = listOf(pathString), globalTestScope = true) { params ->
      @Suppress("UNCHECKED_CAST")
      val path = Path.of(params[0] as String)

      val disposable = Disposer.newDisposable("Skip untrusted project dialog")
      try {
        TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, disposable)

        val projectManager = ProjectManagerEx.getInstanceEx()
        val project = projectManager.openProjectAsync(projectIdentityFile = path, options = OpenProjectTask { forceOpenInNewFrame = true })
                      ?: error("Failed to open project at $path")

        waitSuspending("Project '${project.name}' is initialised", 1.minutes) {
          project.isInitialized
        }
      }
      finally {
        Disposer.dispose(disposable)
      }
    }

    ide.runInFrontend("Wait for project to be opened", globalTestScope = true) {
      waitForProject(timeout = 10.seconds)
    }
  }
}
