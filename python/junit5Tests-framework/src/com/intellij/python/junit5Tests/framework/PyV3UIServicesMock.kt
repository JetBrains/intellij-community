package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.project.Project
import com.jetbrains.python.newProjectWizard.PyV3UIServices
import com.jetbrains.python.util.ErrorSink
import com.jetbrains.python.util.PyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.swing.JComponent

/**
 * Mock for [com.jetbrains.python.newProjectWizard.PyV3UIServices]
 * Collect user errors from [errors], check [projectTreeExpanded] and [kotlinx.coroutines.Job.cancel] the [job] at the end
 */
class PyV3UIServicesMock(private val coroutineScope: CoroutineScope) : PyV3UIServices {
  private val _errors = MutableSharedFlow<PyError>()

  /**
   * [com.jetbrains.python.util.ErrorSink] errors
   */
  val errors: Flow<PyError> = _errors

  @Volatile
  var projectTreeExpanded: Boolean = false
    private set

  /**
   * Cancel at the end
   */
  val job: Job = Job(coroutineScope.coroutineContext.job)
  override fun runWhenComponentDisplayed(component: JComponent, code: suspend CoroutineScope.() -> Unit) {
    coroutineScope.launch(job) {
      code()
    }
  }

  override val errorSink: ErrorSink = _errors

  override suspend fun expandProjectTreeView(project: Project) {
    projectTreeExpanded = true
  }
}