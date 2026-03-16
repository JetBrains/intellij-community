package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectDependencies
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.safeGetArr
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.TomlTable
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

suspend fun getDependenciesFromToml(
  entries: Map<ProjectName, PyProjectTomlProject>,
  rootIndex: Map<Directory, ProjectName>,
  tomlDependencySpecifications: List<TomlDependencySpecification>,
): ProjectDependencies = withContext(Dispatchers.Default) {
  val deps = entries.asSequence().associate { (name, entry) ->
    val depsPaths = collectAllDependencies(entry, tomlDependencySpecifications)
    val deps = processDependenciesWithRootIndex(depsPaths, rootIndex)
    Pair(name, deps)
  }
  ProjectDependencies(deps)
}

private fun processDependenciesWithRootIndex(dependencies: Sequence<Directory>, rootIndex: Map<Directory, ProjectName>): Set<ProjectName> =
  dependencies.mapNotNull { dir ->
    rootIndex[dir] ?: run {
      logger.warn("Can't find project for dir $dir")
      null
    }
  }.toSet()

@RequiresBackgroundThread
private fun collectAllDependencies(
  entry: PyProjectTomlProject, tomlDependencySpecifications: List<TomlDependencySpecification>,
): Sequence<Directory> = sequence {
  yieldAll(getDependenciesFromProject(entry.pyProjectToml))
  yieldAll(getDependenciesFromPep735Groups(entry.pyProjectToml.toml))
  yieldAll(getToolSpecificDependencies(entry.root, entry.pyProjectToml.toml, tomlDependencySpecifications))
}

@RequiresBackgroundThread
private fun getToolSpecificDependencies(
  root: Path, tomlTable: TomlTable, tomlDependencySpecifications: List<TomlDependencySpecification>,
): Sequence<Directory> {
  return tomlDependencySpecifications.asSequence().flatMap { specification ->
    when (specification) {
      is TomlDependencySpecification.PathDependency -> tomlTable.getTable(specification.tomlKey)?.let {
        getToolSpecificDependenciesFromTomlTable(root, it)
      } ?: emptySet()
      is TomlDependencySpecification.Pep621Dependency -> {
        val deps = tomlTable.safeGetArr<String>(specification.tomlKey, unquotedDottedKey = true).successOrNull ?: emptyList()
        deps.asSequence().mapNotNull(::parsePep621Dependency).toSet()
      }
      is TomlDependencySpecification.GroupPathDependency -> {
        val groups = tomlTable.getTable(specification.tomlKeyToGroup) ?: return@flatMap emptySet()
        groups.keySet().flatMap { group ->
          groups.getTable("${group}.${specification.tomlKeyFromGroupToPath}")?.let {
            getToolSpecificDependenciesFromTomlTable(root, it)
          } ?: emptySet()
        }
      }
    }
  }
}

@RequiresBackgroundThread
private fun getToolSpecificDependenciesFromTomlTable(root: Path, tomlTable: TomlTable): Set<Directory> {
  return tomlTable.keySet().asSequence().mapNotNull {
    tomlTable.getString("${it}.path")?.let { depPathString -> parseDepFromPathString(root, depPathString) }
  }.toSet()
}

@RequiresBackgroundThread
private fun getDependenciesFromPep735Groups(tomlTable: TomlTable): Sequence<Directory> {
  val groups = tomlTable.getTable("dependency-groups") ?: return emptySequence()
  return groups.keySet().asSequence().flatMap { group ->
    val deps = groups.safeGetArr<String>(group).successOrNull ?: emptyList()
    deps.asSequence().mapNotNull(::parsePep621Dependency)
  }
}

@RequiresBackgroundThread
private fun getDependenciesFromProject(projectToml: PyProjectToml): Sequence<Directory> {
  val depsFromFile = projectToml.project?.dependencies?.project ?: emptyList()
  return depsFromFile.asSequence().mapNotNull(::parsePep621Dependency)
}

private fun parsePep621Dependency(depSpec: String): Path? {
  val match = PEP_621_PATH_DEPENDENCY.matchEntire(depSpec) ?: return null
  val (_, depUri) = match.destructured
  return parseDepUri(depUri)
}

sealed interface TomlDependencySpecification {
  data class PathDependency(val tomlKey: String) : TomlDependencySpecification
  data class Pep621Dependency(val tomlKey: String) : TomlDependencySpecification
  data class GroupPathDependency(val tomlKeyToGroup: String, val tomlKeyFromGroupToPath: String) : TomlDependencySpecification
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

private fun parseDepFromPathString(root: Path, depPathString: String): Path? =
  try {
    root.resolve(depPathString).normalize()
  }
  catch (e: InvalidPathException) {
    logger.info("Dep $depPathString points to wrong path", e)
    null
  }
