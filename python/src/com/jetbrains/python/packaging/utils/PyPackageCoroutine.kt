package com.jetbrains.python.packaging.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
internal class PyPackageCoroutine(val project: Project, val coroutineScope: CoroutineScope) {
  val ioScope = coroutineScope.childScope("Jupyter IO scope", context = Dispatchers.IO)

  companion object {
    fun launch(project: Project, context: CoroutineContext = Dispatchers.Main, body: suspend CoroutineScope.() -> Unit) =
      project.service<PyPackageCoroutine>().coroutineScope.launch(context, block = body)

    fun getIoScope(project: Project): CoroutineScope = project.service<PyPackageCoroutine>().ioScope
    fun getScope(project: Project): CoroutineScope = project.service<PyPackageCoroutine>().coroutineScope
  }
}
