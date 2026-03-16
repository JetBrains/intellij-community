// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.IssueDocumentationTargetProvider
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.tasks.TaskManager
import com.intellij.tasks.core.LazyTaskSymbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class TaskIssueLinkDocumentationTargetProvider(val cs: CoroutineScope) : IssueDocumentationTargetProvider {

  override fun getIssueDocumentationTarget(project: Project, issueId: String, issueUrl: String): DocumentationTarget? =
    if (TaskManager.getManager(project).allRepositories.any { it.isConfigured && it.extractId(issueId) == issueId })
      LazyTaskSymbol(issueId, issueUrl) {
        cs.async {
          withContext(Dispatchers.IO) {
            TaskManager.getManager(project).allRepositories.firstNotNullOfOrNull { repository ->
              repository.takeIf { it.isConfigured && it.extractId(issueId) == issueId }?.findTask(issueId)
            }
          }
        }.await()
      }.documentationTarget
    else null

}