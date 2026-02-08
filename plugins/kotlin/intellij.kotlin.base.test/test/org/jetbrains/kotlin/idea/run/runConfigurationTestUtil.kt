// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run

import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManagerEx
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.JavaCommandLine
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.MapDataContext
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert

fun getJavaRunParameters(configuration: RunConfiguration): JavaParameters {
    val state =
        configuration.getState(MockExecutor, ExecutionEnvironmentBuilder.create(configuration.project, MockExecutor, MockProfile).build())

    Assert.assertNotNull(state)
    Assert.assertTrue(state is JavaCommandLine)

    configuration.checkConfiguration()
    return (state as JavaCommandLine).javaParameters!!
}

fun createConfigurationFromElement(element: PsiElement, save: Boolean = false): RunConfiguration {
    val dataContext = MapDataContext()
    dataContext.put(Location.DATA_KEY, PsiLocation(element.project, element))

    val runnerAndConfigurationSettings =
        ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN).configuration
            ?: error("no runnerAndConfigurationSettings for $element [${(element as? KtNamedFunction)?.fqName?.asString()}]")
    if (save) {
        RunManagerEx.getInstanceEx(element.project).setTemporaryConfiguration(runnerAndConfigurationSettings)
    }
    return runnerAndConfigurationSettings.configuration
}

fun createLibraryWithLongPaths(project: Project, testRootDisposable: Disposable): Library {
    val maxCommandlineLengthWindows = 24500
    val maxFilenameLengthWindows = 245

    return runWriteAction {
        val modifiableModel = ProjectLibraryTable.getInstance(project).modifiableModel
        val library = with(modifiableModel) {
            try {
                createLibrary("LibraryWithLongPaths", null)
            } finally {
                commit()
            }
        }

        with(library.modifiableModel) {
            for (i in 0..maxCommandlineLengthWindows / maxFilenameLengthWindows) {
                val tmpFile = VirtualFileManager.constructUrl(
                    LocalFileSystem.getInstance().protocol,
                    FileUtil.createTempDirectory("file$i", "a".repeat(maxFilenameLengthWindows)).path
                )
                addRoot(tmpFile, OrderRootType.CLASSES)
            }
            commit()
        }
        library
    }.also { library ->
        Disposer.register(testRootDisposable) {
            runWriteAction {
                val modifiableModel = ProjectLibraryTable.getInstance(project).modifiableModel
                with(modifiableModel) {
                    try {
                        removeLibrary(library)
                    } finally {
                        commit()
                    }
                }
            }
        }
    }

}


private object MockExecutor : DefaultRunExecutor() {
    override fun getId() = EXECUTOR_ID
}

private object MockProfile : RunProfile {
    override fun getState(executor: Executor, env: ExecutionEnvironment) = null
    override fun getIcon() = null
    override fun getName() = "unknown"
}
