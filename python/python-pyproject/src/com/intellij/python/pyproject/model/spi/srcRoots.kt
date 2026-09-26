// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.spi

import com.intellij.openapi.diagnostic.fileLogger
import com.jetbrains.python.venvReader.Directory
import java.nio.file.InvalidPathException

private val logger = fileLogger()

/**
 * Turns the directory paths a build backend declares in `pyproject.toml` into source roots.
 * Use it to implement [PyProjectManager.getSrcRoots].
 *
 * [relativePaths] are paths as the toml spells them, relative to [projectRoot].
 * A path is dropped when it leaves [projectRoot], or when it is [projectRoot] itself. The last rule
 * keeps the flat layout as it is: such a project holds its packages in the content root and needs
 * no source root (PY-88898).
 *
 * The function touches no disk. A declared directory that does not exist yet is still a source root,
 * because the user can create it later without a change to `pyproject.toml`.
 */
fun resolveSrcRoots(projectRoot: Directory, relativePaths: Collection<String>): Set<Directory> {
  val root = projectRoot.normalize()
  val srcRoots = mutableSetOf<Directory>()
  for (path in relativePaths) {
    val dir = try {
      root.resolve(path).normalize()
    }
    catch (e: InvalidPathException) {
      logger.info("Can't resolve '$path' against '$root'", e)
      continue
    }
    if (dir != root && dir.startsWith(root)) {
      srcRoots.add(dir)
    }
  }
  return srcRoots
}
