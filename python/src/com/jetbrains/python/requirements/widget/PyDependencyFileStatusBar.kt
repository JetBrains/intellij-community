// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.widget

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.wm.IconWidgetPresentation
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WidgetPresentation
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.openapi.wm.WidgetPresentationFactory
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.widget.resolvePythonWidgetContext
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.requirements.PyRequirementsBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import java.awt.event.MouseEvent
import javax.swing.Icon

private const val ID: String = "pythonDependenciesFileWidget"

internal class PyDependenciesFileStatusBarWidgetFactory : StatusBarWidgetFactory, WidgetPresentationFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = PyRequirementsBundle.message("python.requirements.dependencies.file.widget.display.name")

  override fun createPresentation(context: WidgetPresentationDataContext, scope: CoroutineScope): WidgetPresentation {
    return PyDependenciesFileWidget(context)
  }
}

private class PyDependenciesFileWidget(private val context: WidgetPresentationDataContext) : IconWidgetPresentation {
  private val project: Project = context.project

  @Volatile
  private var currentState: State? = null

  private data class State(val module: Module, val file: PyDependenciesFile)

  override fun icon(): Flow<Icon?> {
    val rootsChangedTicks = callbackFlow {
      val connection = project.messageBus.connect(this)
      connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          trySend(Unit)
        }
      })
      awaitClose { connection.disconnect() }
    }.onStart { emit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    return merge(context.currentFileEditor, rootsChangedTicks).mapLatest {
      val state = computeState()
      currentState = state
      state?.file?.icon
    }
  }

  override suspend fun getTooltipText(): String? {
    val state = currentState ?: return null
    return PyRequirementsBundle.message("python.requirements.dependencies.file.widget.tooltip.with.module",
                                        state.module.name,
                                        state.file.virtualFile.name)
  }

  override fun getClickConsumer(): (MouseEvent) -> Unit = {
    currentState?.let { state ->
      FileEditorManager.getInstance(project).openFile(state.file.virtualFile, true)
    }
  }

  private suspend fun computeState(): State? {
    val virtualFile = context.currentFileEditor.value?.file
    val (module, sdk) = readAction { resolvePythonWidgetContext(project, virtualFile) } ?: return null
    if (sdk == null) return null
    val packageManager = PythonPackageManager.forSdk(project, sdk)
    val depFile = packageManager.getRootDependenciesFile() ?: return null
    return State(module, depFile)
  }
}
