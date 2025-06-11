package com.intellij.mcpserver

import com.intellij.openapi.project.Project
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class ProjectContextElement(val project: Project?) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<ProjectContextElement>
}

/**
 * The most relevant project. Usually it's obtained via the last focused frame.
 */
val CoroutineContext.projectOrNull: Project?
  get() = this[ProjectContextElement.Key]?.project

/**
 * The same as [projectOrNull], but throws an McpExpectedError if no project is open.
 */
val CoroutineContext.project: Project
  get() = this[ProjectContextElement.Key]?.project ?: throw McpExpectedError("No project opened")
