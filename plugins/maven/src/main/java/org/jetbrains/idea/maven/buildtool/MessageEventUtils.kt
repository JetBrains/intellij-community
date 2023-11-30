// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.execution.ExecutionException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ExceptionUtil
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.CannotStartServerException

internal fun createMessageEvent(project: Project, taskId: ExternalSystemTaskId, e: Throwable): MessageEventImpl {
  var error = e
  var group = SyncBundle.message("build.event.title.error")

  val csse = ExceptionUtil.findCause(e, CannotStartServerException::class.java)
  if (csse != null) {
    group = SyncBundle.message("build.event.title.internal.server.error")
    error = ExceptionUtil.findCause(csse, ExecutionException::class.java) ?: csse
  }

  val message = getExceptionText(project, error)
  return MessageEventImpl(taskId, MessageEvent.Kind.ERROR, group, message, message)
}

private fun getExceptionText(project: Project, e: Throwable): @NlsSafe String {
  val generalSettings = MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings
  if (null != generalSettings && generalSettings.isPrintErrorStackTraces) {
    return ExceptionUtil.getThrowableText(e)
  }

  if (!e.localizedMessage.isNullOrEmpty()) return e.localizedMessage
  return if (StringUtil.isEmpty(e.message)) SyncBundle.message("build.event.title.error") else e.message!!
}
