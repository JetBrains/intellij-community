// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator

val POST_TASKS_KEY = Key.create<MutableList<MavenPostTask>>("postTasks")

interface MavenPostTask {
  @Throws(MavenProcessCanceledException::class)
  fun perform(project: Project, mavenProgressIndicator: MavenProgressIndicator)
}

class MavenPostTaskConfigurator : MavenAfterImportConfigurator {
  override fun afterImport(context: MavenAfterImportConfigurator.Context) {
    val postTasks = context.getUserData(POST_TASKS_KEY)
    if (null == postTasks) return
    postTasks.forEach { it.perform(context.project, context.mavenProgressIndicator) }
  }
}