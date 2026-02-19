package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectDependencies
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileVisitResult
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.visitFileTree

// Tools to walk FS and parse pyproject.toml
/**
 * Walks down the [root]. Like [walkFileSystemNoTomlContent] but with TOML files content
 */
internal suspend fun walkFileSystemWithTomlContent(root: Directory): Result<FSWalkInfoWithToml, IOException> {
  val rawTomlFiles = walkFileSystemNoTomlContent(root).getOr { return it }.rawTomlFiles

  // TODO: with a big number of files, use `chunk` to parse them concurrently
  val tomlFiles = rawTomlFiles.map { file ->
    val toml = readFile(file) ?: return@map null
    file to toml
  }.filterNotNull().toMap()
  return Result.success(FSWalkInfoWithToml(tomlFiles = tomlFiles))
}

/**
 * Walks down [root], returns all [PY_PROJECT_TOML]  (started with dot).
 * [IOException] is returned if [root] is inaccessible
 */
suspend fun walkFileSystemNoTomlContent(
  root: Directory,
): Result<FsWalkInfoNoToml, IOException> {
  val rawTomlFiles = ArrayList<Path>(10)
  try {
    withContext(Dispatchers.IO) {
      root.visitFileTree {
        onVisitFile { file, _ ->
          if (file.name == PY_PROJECT_TOML) {
            rawTomlFiles.add(file)
          }
          return@onVisitFile FileVisitResult.CONTINUE
        }
        onPreVisitDirectory { directory, _ ->
          val dirName = directory.name

          // default name is popular enough to make a shortcut
          if (dirName == VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME
              || VirtualEnvReader().findPythonInPythonRoot(directory) != null) {
            // Venv: exclude and skip
            FileVisitResult.SKIP_SUBTREE
          }
          else if (dirName.startsWith(".")) {
            // Dot: just skip
            FileVisitResult.SKIP_SUBTREE
          }
          else {
            FileVisitResult.CONTINUE
          }
        }
      }
    }
    return Result.success(FsWalkInfoNoToml(rawTomlFiles = rawTomlFiles))
  }
  catch (e: IOException) {
    return Result.failure(e)
  }
}


suspend fun getPEP621Deps(
  entries: Map<ProjectName, PyProjectTomlProject>,
  rootIndex: Map<Directory, ProjectName>,
): ProjectDependencies = withContext(Dispatchers.Default) {
  val deps = entries.asSequence().associate { (name, entry) ->
    val deps = getDependenciesFromToml(entry.pyProjectToml).mapNotNull { dir ->
      rootIndex[dir] ?: run {
        logger.warn("Can't find project for dir $dir")
        null
      }
    }.toSet()
    Pair(name, deps)
  }
  ProjectDependencies(deps)  // No workspace info (yet)
}

private val logger = fileLogger()

private suspend fun readFile(file: Path): PyProjectToml? {
  val content = try {
    withContext(Dispatchers.IO) { file.readText() }
  }
  catch (e: IOException) {
    logger.warn("Can't read $file", e)
    return null
  }
  return withContext(Dispatchers.Default) {
    val toml = PyProjectToml.parse(content)
    val errors = toml.issues.joinToString(", ")
    if (errors.isNotBlank()) {
      logger.warn("Errors on $file: $errors")
    }
    toml
  }
}

@RequiresBackgroundThread
private fun getDependenciesFromToml(projectToml: PyProjectToml): Set<Directory> {
  val depsFromFile = projectToml.project?.dependencies?.project ?: emptyList()
  val moduleDependencies = depsFromFile
    .mapNotNull { depSpec ->
      val match = PEP_621_PATH_DEPENDENCY.matchEntire(depSpec) ?: return@mapNotNull null
      val (_, depUri) = match.destructured
      return@mapNotNull parseDepUri(depUri)
    }
  return moduleDependencies.toSet()
}


// e.g. "lib @ file:///home/user/projects/main/lib"
private val PEP_621_PATH_DEPENDENCY = """([\w-]+) @ (file:.*)""".toRegex()

private fun parseDepUri(depUri: String): Path? =
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