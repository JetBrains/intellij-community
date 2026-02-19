// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.test.assertNull
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener.EP_NAME as EP

interface GradleProcessOutputInterceptor {
    companion object {
        fun getInstance(): GradleProcessOutputInterceptor? = EP.extensions.firstIsInstanceOrNull()

        fun install(parentDisposable: Disposable) {
            val installedExtensions = EP.extensions

            val actual = installedExtensions.firstIsInstanceOrNull<GradleProcessOutputInterceptor>()
            if (actual is GradleProcessOutputInterceptorImpl) return

            assertNull(
                actual,
                "Another ${GradleProcessOutputInterceptor::class.java.simpleName} is already installed"
            )

            maskExtensions(
                EP,
                listOf(GradleProcessOutputInterceptorImpl()) + installedExtensions,
                parentDisposable
            )
        }
    }

    fun reset()
    fun getOutput(): String
}

private class GradleProcessOutputInterceptorImpl : GradleProcessOutputInterceptor, ExternalSystemTaskNotificationListener {
    private val buffer = StringBuilder()

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
        if (id.projectSystemId == GRADLE_SYSTEM_ID && text.isNotEmpty()) {
            buffer.append(text)
        }
    }

    override fun reset() = buffer.setLength(0)
    override fun getOutput() = buffer.toString()
}
