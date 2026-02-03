package com.intellij.python.processOutput.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.processOutput.ProcessOutputApi
import org.jetbrains.annotations.Nls

internal class ProcessOutputApiImpl : ProcessOutputApi {
    override fun specifyAdditionalMessageToUser(
        project: Project,
        logId: Int,
        text: @Nls String,
    ) {
        val service = project.service<ProcessOutputControllerService>()
        service.specifyAdditionalMessageToUser(logId, text)
    }

    override fun tryOpenLogInToolWindow(
        project: Project,
        logId: Int,
    ): Boolean {
        val service = project.service<ProcessOutputControllerService>()
        return service.tryOpenLogInToolWindow(logId)
    }
}
