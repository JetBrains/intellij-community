package com.intellij.python.pyproject.model.internal.pyProjectToml

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectDependencies
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.intellij.python.pyproject.safeGet
import com.intellij.python.pyproject.safeGetArr
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.PRUNED_SCAN_DIRS_NO_DOT
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.TomlTable
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileVisitResult
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.toPath
import kotlin.io.path.visitFileTree

// Tools to walk FS and parse pyproject.toml
/**
 * Walks down the [root]. Like [walkFileSystemNoTomlContent] but with TOML files content
 */
internal suspend fun walkFileSystemWithTomlContent(
  roots: Set<Directory>,
  excludedPaths: Set<Path> = emptySet(),
): Result<FSWalkInfoWithToml, IOException> {
  val rawTomlFiles = walkFileSystemNoTomlContent(roots, excludedPaths).getOr { return it }.rawTomlFiles

  // `awaitAll` keeps the order of `rawTomlFiles`, so the resulting map stays deterministic.
  val tomlFiles = coroutineScope {
    rawTomlFiles
      .map { file -> async(Dispatchers.Default) { readFile(file)?.let { toml -> file to toml } } }
      .awaitAll()
      .filterNotNull()
      .toMap()
  }
  return Result.success(FSWalkInfoWithToml(tomlFiles = tomlFiles))
}

/**
 * Walks down [roots], returns all [PY_PROJECT_TOML]  (started with dot).
 * [IOException] is returned if one of the [roots] is inaccessible
 */
suspend fun walkFileSystemNoTomlContent(
  roots: Set<Directory>,
  excludedPaths: Set<Path> = emptySet(),
): Result<FsWalkInfoNoToml, IOException> {
  val rawTomlFiles = ArrayList<Path>(10)
  // TODO: Measure performance, parallelize if needed
  try {
    withContext(Dispatchers.IO) {
      for (root in roots) {
        walkFileSystemNoTomlContent(root, rawTomlFiles, excludedPaths)
      }
    }
  }
  catch (e: IOException) {
    return Result.failure(e)
  }
  return Result.success(FsWalkInfoNoToml(rawTomlFiles = rawTomlFiles))
}

@Throws(IOException::class)
@RequiresBackgroundThread
private fun walkFileSystemNoTomlContent(
  root: Directory,
  rawTomlFiles: MutableList<Path>,
  excludedPaths: Set<Path>,
) {
  val virtualEnvReader = VirtualEnvReader()
  root.visitFileTree {
    onVisitFile { file, _ ->
      if (file.name == PY_PROJECT_TOML) {
        rawTomlFiles.add(file)
      }
      return@onVisitFile FileVisitResult.CONTINUE
    }
    // The order of the checks follows their cost. A check of the name needs no syscall.
    // Only a directory that passes the name checks pays for the `stat` of the venv marker.
    // This walk visits every directory of the project, so the order is important (PY-91826).
    onPreVisitDirectory { directory, _ ->
      val dirName = directory.name

      // A dot directory, a well-known heavy directory, or an excluded directory.
      // A `pyproject.toml` in an excluded folder must not become a module.
      if (dirName.startsWith(".") || dirName in PRUNED_SCAN_DIRS_NO_DOT || directory in excludedPaths) {
        FileVisitResult.SKIP_SUBTREE
      }
      // A venv. The walk never descends into an environment.
      else if (virtualEnvReader.findPythonInPythonRoot(directory) != null) {
        FileVisitResult.SKIP_SUBTREE
      }
      else {
        FileVisitResult.CONTINUE
      }
    }
  }
}

private val logger = fileLogger()

/**
 * The caller selects the dispatcher for the parse, because [PyProjectToml.parse] runs on it.
 * [PyProjectToml.parseOrNull] reads the file on [Dispatchers.IO] itself.
 */
private suspend fun readFile(file: Path): PyProjectToml? {
  val toml = PyProjectToml.parseOrNull(file) ?: return null
  if (toml.issues.isNotEmpty()) {
    logger.warn("Errors on $file: ${toml.issues.joinToString(", ")}")
  }
  return toml
}

internal suspend fun getDependenciesFromToml(
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
  yieldAll(getDeclaredPathDependencies(entry.root, entry.pyProjectToml))
  yieldAll(getToolSpecificDependencies(entry.root, entry.pyProjectToml.toml, tomlDependencySpecifications))
}

@RequiresBackgroundThread
private fun getToolSpecificDependencies(
  root: Path, tomlTable: TomlTable, tomlDependencySpecifications: List<TomlDependencySpecification>,
): Sequence<Directory> {
  return tomlDependencySpecifications.asSequence().flatMap { specification ->
    when (specification) {
      // PY-91089: use safeGet instead of TomlTable.getTable, which throws TomlInvalidTypeException
      // (not returns null) when the key holds a non-table value such as an array (the `[[tool.uv.sources]]`
      // double-bracket typo). An unhandled throw here aborts the whole model sync and hides every member.
      is TomlDependencySpecification.PathDependency -> tomlTable.safeGet<TomlTable>(specification.tomlKey,
                                                                                    unquotedDottedKey = true).successOrNull?.let {
        getToolSpecificDependenciesFromTomlTable(root, it)
      } ?: emptySet()
      is TomlDependencySpecification.Pep621Dependency -> getPep621Dependencies(root, tomlTable, specification.tomlKey).toSet()
      is TomlDependencySpecification.GroupPathDependency -> {
        val groups =
          tomlTable.safeGet<TomlTable>(specification.tomlKeyToGroup, unquotedDottedKey = true).successOrNull ?: return@flatMap emptySet()
        groups.keySet().flatMap { group ->
          // A group name is a single literal key that may itself contain a dot, so look it up quoted
          // and only then descend into the nested key.
          groups.safeGet<TomlTable>(group, unquotedDottedKey = false).successOrNull
            ?.safeGet<TomlTable>(specification.tomlKeyFromGroupToPath, unquotedDottedKey = false)?.successOrNull?.let {
              getToolSpecificDependenciesFromTomlTable(root, it)
            } ?: emptySet()
        }
      }
      is TomlDependencySpecification.GroupPep621Dependency -> {
        val groups =
          tomlTable.safeGet<TomlTable>(specification.tomlKeyToGroup, unquotedDottedKey = true).successOrNull ?: return@flatMap emptySet()
        groups.keySet().flatMap { group ->
          val groupTable = groups.safeGet<TomlTable>(group, unquotedDottedKey = false).successOrNull
          if (groupTable == null) emptySet()
          else getPep621Dependencies(root, groupTable, specification.tomlKeyFromGroupToDependencies)
        }
      }
    }
  }
}

@RequiresBackgroundThread
private fun getPep621Dependencies(root: Path, tomlTable: TomlTable, tomlKeyToDependencies: String): Set<Path> {
  val deps = tomlTable.safeGetArr<String>(tomlKeyToDependencies, unquotedDottedKey = true).successOrNull ?: return emptySet()
  return deps.asSequence().mapNotNull { parsePep621Dependency(root, it) }.toSet()
}

@RequiresBackgroundThread
private fun getToolSpecificDependenciesFromTomlTable(root: Path, tomlTable: TomlTable): Set<Directory> {
  return tomlTable.keySet().asSequence().mapNotNull { depName ->
    // PY-91089: safeGet instead of getString, which throws when `<dep>.path` holds a non-string value.
    // PY-90207: the dependency name is one literal key and may contain a dot (`zope.interface`,
    // `ruamel.yaml`), so it must be looked up quoted; `path` is then read from the nested table.
    tomlTable.safeGet<TomlTable>(depName, unquotedDottedKey = false).successOrNull
      ?.safeGet<String>("path", unquotedDottedKey = false)?.successOrNull?.let { depPathString ->
        parseDepFromPathString(root, depPathString)
      }
  }.toSet()
}

/**
 * Path dependencies (`lib @ file:///...`) among every dependency this file declares: `project.dependencies`,
 * the `project.optional-dependencies` extras, and the PEP 735 `dependency-groups`.
 */
@RequiresBackgroundThread
private fun getDeclaredPathDependencies(root: Path, projectToml: PyProjectToml): Sequence<Directory> =
  projectToml.allDeclaredDeps.asSequence().mapNotNull { parsePep621Dependency(root, it) }

private fun parsePep621Dependency(root: Path, depSpec: String): Path? {
  val match = PEP_621_PATH_DEPENDENCY.matchEntire(depSpec) ?: return null
  val (_, directReference) = match.destructured
  return when {
    directReference.startsWith("file:") -> parseDepUri(directReference)
    else -> parseHatchContextFormattedPath(root, directReference)
  }
}


// e.g. "lib @ file:///home/user/projects/main/lib"
private val PEP_621_PATH_DEPENDENCY = """([\w-]+) @ (.*)""".toRegex()

// e.g. "{root:parent:uri}/lib"
private val HATCH_ROOT_URI = """\{root((?::parent)*):uri}(/.*)?""".toRegex()

private fun parseHatchContextFormattedPath(root: Path, directReference: String): Path? {
  val match = HATCH_ROOT_URI.matchEntire(directReference) ?: return null
  val (parentModifiers, relativePath) = match.destructured
  val parentCount = parentModifiers.split(':').count { it == "parent" }
  val formattedRoot = root.nthParent(parentCount) ?: return null
  return parseDepFromPathString(formattedRoot, relativePath.removePrefix("/"))
}

private fun Path.nthParent(count: Int): Path? {
  var current: Path? = this
  for (i in 0 until count) {
    current = current?.parent ?: return null
  }
  return current
}

private fun parseDepUri(depUri: String): Path? = try {
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

private fun parseDepFromPathString(root: Path, depPathString: String): Path? = try {
  root.resolve(depPathString).normalize()
}
catch (e: InvalidPathException) {
  logger.info("Dep $depPathString points to wrong path", e)
  null
}
