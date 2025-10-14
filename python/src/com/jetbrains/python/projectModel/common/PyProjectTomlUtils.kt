// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.common

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.toPath

@RequiresBackgroundThread
internal fun getDependenciesFromToml(projectToml: PyProjectToml): Set<Directory> {
  val depsFromFile = projectToml.project?.dependencies?.project ?: emptyList()
  val moduleDependencies = depsFromFile
    .mapNotNull { depSpec ->
      val match = PEP_621_PATH_DEPENDENCY.matchEntire(depSpec) ?: return@mapNotNull null
      val (_, depUri) = match.destructured
      return@mapNotNull parseDepUri(depUri)
    }
  return moduleDependencies.toSet()
}

internal suspend fun getProjectStructure(
  entries: Map<ProjectName, PyProjectTomlProject>,
  rootIndex: Map<Directory, ProjectName>,
  dependenciesGetter: (PyProjectTomlProject) -> Set<Directory>,
): ProjectStructureInfo = withContext(Dispatchers.Default) {
  val deps = entries.asSequence().map { (name, entry) ->
    val deps = dependenciesGetter(entry).mapNotNull { dir ->
      rootIndex[dir] ?: run {
        logger.warn("Can't find project for dir $dir")
        null
      }
    }.toSet()
    Pair(name, deps)
  }.toMap()
  ProjectStructureInfo(dependencies = deps, membersToWorkspace = emptyMap()) // No workspace info (yet)
}

// e.g. "lib @ file:///home/user/projects/main/lib"
private val PEP_621_PATH_DEPENDENCY = """([\w-]+) @ (file:.*)""".toRegex()

private val logger = fileLogger()
internal fun parseDepUri(depUri: String): Path? =
  try {
    URI(depUri).toPath()
  }
  catch (e: InvalidPathException) {
    logger.info("Dep $depUri points to wrong path", e)
    null
  }
  catch (e: URISyntaxException) {
    logger.info("Dep $depUri can't be parsed", e)
    null
  }