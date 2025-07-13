package com.jetbrains.python.packaging.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyPackageCoroutine(val project: Project, val coroutineScope: CoroutineScope) : Disposable.Default {
  companion object {
    fun getInstance(project: Project): PyPackageCoroutine = project.service<PyPackageCoroutine>()
    fun launch(project: Project?, context: CoroutineContext = Dispatchers.Main, start: CoroutineStart = CoroutineStart.DEFAULT, body: suspend CoroutineScope.() -> Unit): Job {
      return project?.service<PyPackageCoroutine>()?.coroutineScope?.launch(context, block = body, start = start)
             ?: ApplicationManager.getApplication().service<PyAppCoroutine>().coroutineScope.launch(context, block = body, start = start)
    }

    fun getScope(project: Project): CoroutineScope = project.service<PyPackageCoroutine>().coroutineScope
  }
}

@Service(Service.Level.APP)
@ApiStatus.Internal
private class PyAppCoroutine(val coroutineScope: CoroutineScope) : Disposable.Default
