package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.utils.waitSuspending
import com.intellij.lambda.testFramework.testApi.utils.waitSuspendingForOne
import com.intellij.lambda.testFramework.testApi.utils.waitSuspendingNotNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.ui.AppIcon
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun getProjects(): List<Project> = ProjectManagerEx.getOpenProjects()

/**
 * If there is one project -> this project
 * If there is one FOCUSED project -> this project
 * If there is one project with the name not matching one set in TestProperties.additionalProject -> this project
 * else null
 *
 * @see also getProject()
 */

context(lambdaIdeContext: LambdaIdeContext)
fun getProjectOrNull(): Project? {
  val projects = getProjects()
  return projects.singleOrNull()
}

context(lambdaIdeContext: LambdaIdeContext)
fun getProject(): Project =
  getProjectOrNull() ?: error("Have not been able to find project")

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