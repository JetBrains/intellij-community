package com.intellij.lambda.testFramework.testApi

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingForOne
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import com.intellij.ui.AppIcon
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun getProjects(): List<Project> = ProjectManagerEx.getOpenProjects()

context(lambdaIdeContext: LambdaIdeContext)
fun getProjectOrNull(): Project? {
  val projects = getProjects()
  return projects.singleOrNull()
}

context(lambdaIdeContext: LambdaIdeContext)
fun getProject(): Project =
  getProjects().single()

context(lambdaIdeContext: LambdaIdeContext)
val Project.frame
  get() = frameOrNull ?: error("No Ide frame found")

context(lambdaIdeContext: LambdaIdeContext)
private val Project.frameOrNull
  get() = WindowManager.getInstance().getFrame(this)

context(lambdaIdeContext: LambdaIdeContext)
val Project.isFocused: Boolean
  get() = frameOrNull?.isFocusAncestor() ?: false

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForProject(projectName: String, timeout: Duration = 5.seconds): Project =
  waitSuspendingForOne("There is project '$projectName'", timeout,
                       getter = { getProjects() },
                       checker = { it.name == projectName })

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForProject(timeout: Duration = 5.seconds): Project =
  waitSuspendingNotNull("There is a project", timeout) {
    getProjectOrNull()
  }

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForNoProjects(timeout: Duration = 5.seconds) =
  waitSuspending("There is no project", timeout,
                 getter = { getProjects() },
                 checker = { it.isEmpty() })

context(lambdaIdeContext: LambdaIdeContext)
suspend fun focusProject(projectName: String) {
  waitForProject(projectName).requestAndWaitFocus()
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun Project.requestAndWaitFocus() {
  if (!isFocused) {
    frameworkLogger.info("Focus Project '${name}'")
    AppIcon.getInstance().requestFocus(frame)
    frame.requestFocus()
    waitSuspending("Project '${name}' is focused after forced requestFocus",
                   10.seconds) {
      isFocused
    }
  }
  else {
    frameworkLogger.info("Project '${name}' is already focused")
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun Project.waitInitialised() {
  waitSuspending("Project '$name 'is initialised", 1.minutes) {
    isInitialized
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun Project.waitHasVisibleFrame(timeout: Duration = 1.minutes) {
  waitSuspending("Project '$name' has visible frame", timeout) {
    frameOrNull?.isVisible == true
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun Project.waitHasNoVisibleFrame(timeout: Duration = 1.minutes) {
  waitSuspending("Project '$name' has visible frame", timeout) {
    frameOrNull == null
  }
}

/**
 * Opens a new project from the given path.
 *
 * @param projectPath the path to the project directory
 * @return the opened and initialized project
 */
context(lambdaBackendContext: LambdaBackendContext)
suspend fun openProject(projectPath: Path): Project {
  frameworkLogger.info("Opening project at $projectPath")
  TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, lambdaBackendContext.globalDisposable)
  val projectManager = ProjectManagerEx.getInstanceEx()
  val project = projectManager.openProjectAsync(
    projectIdentityFile = projectPath,
    options = OpenProjectTask {
      forceOpenInNewFrame = true
    }
  ) ?: error("Failed to open project at $projectPath")

  lambdaBackendContext.addAfterEachCleanup {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      projectManager.forceCloseProjectAsync(project, save = false)
      waitForProject(timeout = 5.seconds) // waits there is a single project left
    }
  }
  project.waitInitialised()
  frameworkLogger.info("Project '${project.name}' opened and initialized")
  return project
}