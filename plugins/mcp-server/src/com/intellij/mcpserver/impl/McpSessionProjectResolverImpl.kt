// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.mcpserver.noSuitableProjectError
import com.intellij.mcpserver.util.findMostRelevantProject
import com.intellij.mcpserver.util.findMostRelevantProjectForRoots
import com.intellij.mcpserver.util.getPathForMcp
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project

private val logger = fileLogger()

class McpSessionProjectResolverImpl: McpSessionProjectResolver {
  override suspend fun resolveSessionProject(
    projectPathFromArgument: String?,
    projectPathFromCallHeader: String?,
    projectPathFromSessionHeader: String?,
    roots: Set<String>,
  ): Project {
    logger.trace {
      "Resolving project... ${projectPathParameterName}: $projectPathFromArgument, callHeader: $projectPathFromCallHeader, " +
      "sessionHeader: $projectPathFromSessionHeader, roots: $roots"
    }

    if (!projectPathFromArgument.isNullOrBlank()) {
      logger.trace { "Resolving project in strict mode by `${projectPathParameterName}`=$projectPathFromArgument..." }
      val project = findMostRelevantProject(projectPathFromArgument)
      if (project != null) {
        logger.trace { "Resolved project ${project.getPathForMcp()} from `${projectPathParameterName}`=$projectPathFromArgument" }
        return project
      }

      logger.trace { "Project not found for `${projectPathParameterName}`=$projectPathFromArgument" }
      throw noSuitableProjectError("`${projectPathParameterName}`=`$projectPathFromArgument` doesn't correspond to any open project.")
    }

    logger.trace { "Resolving project in chaining mode..." }

    if (!projectPathFromCallHeader.isNullOrBlank()) {
      logger.trace { "Trying call header project path: $projectPathFromCallHeader..." }
      val project = findMostRelevantProject(projectPathFromCallHeader)
      if (project != null) {
        logger.trace { "Resolved project ${project.getPathForMcp()} from call header project path: $projectPathFromCallHeader" }
        return project
      }

      logger.trace { "Project not found for call header project path: $projectPathFromCallHeader" }
    }
    else {
      logger.trace { "Skipping call header project resolution because call header is empty" }
    }

    if (!projectPathFromSessionHeader.isNullOrBlank()) {
      logger.trace { "Trying session header project path: $projectPathFromSessionHeader..." }
      val project = findMostRelevantProject(projectPathFromSessionHeader)
      if (project != null) {
        logger.trace { "Resolved project ${project.getPathForMcp()} from session header project path: $projectPathFromSessionHeader" }
        return project
      }

      logger.trace { "Project not found for session header project path: $projectPathFromSessionHeader" }
    }
    else {
      logger.trace { "Skipping session header project resolution because session header is empty" }
    }

    if (roots.isNotEmpty()) {
      logger.trace { "Trying roots project resolution: $roots..." }
      val project = findMostRelevantProjectForRoots(roots)
      if (project != null) {
        logger.trace { "Resolved project ${project.getPathForMcp()} from roots: $roots" }
        return project
      }

      logger.trace { "Project not found for roots: $roots" }
    }
    else {
      logger.trace { "Skipping roots project resolution because roots are empty" }
    }

    throw noSuitableProjectError("Unable to determine the target project for the current MCP tool call.")
  }
}