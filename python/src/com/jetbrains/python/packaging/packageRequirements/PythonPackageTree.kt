// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.packageRequirements

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PyDependencyGroupName
import com.jetbrains.python.packaging.common.PythonPackage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

/**
 * Base type for all package tree nodes.
 */
@ApiStatus.Internal
sealed class PackageStructureNode

/**
 * Represents a single package with its transitive dependencies.
 */
@ApiStatus.Internal
data class PackageTreeNode(
  val name: PyPackageName,
  val children: MutableList<PackageTreeNode> = mutableListOf(),
  val group: String? = null,
  val version: String? = null,
)

/**
 * Represents a workspace member with its sub-members and package dependency tree.
 *
 * @property name The name of the workspace member
 * @property subMembers List of nested workspace members (from pyproject.toml)
 * @property packageTree The dependency tree for this member's packages
 * @property undeclaredPackages Packages not declared in workspace but installed
 */
@ApiStatus.Internal
data class WorkspaceMemberPackageStructureNode(
  val name: String,
  val subMembers: List<WorkspaceMemberPackageStructureNode>,
  var packageTree: PackageTreeNode?,
  val undeclaredPackages: List<PackageTreeNode> = emptyList()
) : PackageStructureNode()

/**
 * Represents a flat collection of packages (non-workspace structure).
 *
 * @property declaredPackages Packages explicitly declared in project dependencies
 * @property undeclaredPackages Packages installed but not declared (transitive or manual)
 */
@ApiStatus.Internal
data class PackageCollectionPackageStructureNode(
  val declaredPackages: List<PackageTreeNode>,
  val undeclaredPackages: List<PackageTreeNode>
) : PackageStructureNode()

/**
 * Indicates that all installed packages are considered declared (no tree structure available).
 * Used by simple package managers (e.g. pip) that don't distinguish declared from transitive.
 */
@ApiStatus.Internal
data object FlatPackageStructureNode : PackageStructureNode()

/**
 * Iteratively collects all package names from this node and its descendants.
 */
@ApiStatus.Internal
fun PackageTreeNode.collectAllNames(): Set<String> {
  val result = mutableSetOf<String>()
  val toVisit = ArrayDeque<PackageTreeNode>()
  toVisit.addLast(this)
  while (toVisit.isNotEmpty()) {
    val node = toVisit.removeLast()
    if (result.add(node.name.name)) {
      toVisit.addAll(node.children)
    }
  }
  return result
}

@ApiStatus.Internal
object TreeParser {
  private data class ParseResult(
    val node: PackageTreeNode,
    val nextIndex: Int,
  )

  // Box-drawing characters used in tree output from package managers (uv, poetry, pip)
  private const val VERTICAL = '│'
  private const val BRANCH = '├'
  private const val CORNER = '└'
  private const val HORIZONTAL = '─'
  // ASCII fallbacks some tools use
  private const val VERTICAL_ASCII = '|'
  private const val CORNER_ASCII = '`'
  private const val HORIZONTAL_ASCII = '-'

  private val INDENT_PREFIXES = charArrayOf(' ', VERTICAL, BRANCH, CORNER, VERTICAL_ASCII, CORNER_ASCII)

  private val TREE_LINE_REGEX = Regex(
    """^[\s${VERTICAL}${VERTICAL_ASCII}${CORNER_ASCII}]*[${BRANCH}${CORNER}${CORNER_ASCII}${VERTICAL_ASCII}][${HORIZONTAL_ASCII}${HORIZONTAL}]+ """
  )
  private val GROUP_REGEX = Regex("""\((?:group|extra):\s*([\w.-]+)\)""")
  private const val SPACE_DELIMITER = ' '
  private const val VERSION_DELIMITER = '['

  fun parseTrees(lines: List<String>): List<PackageTreeNode> {
    val nonBlankLines = lines.withIndex().filterNot { it.value.isBlank() }
    val result = mutableListOf<PackageTreeNode>()
    var currentIndex = 0

    while (currentIndex < nonBlankLines.size) {
      val (originalIndex, line) = nonBlankLines[currentIndex]
      val (node, nextIndex) = parseLevel(lines, calculateIndentLevel(line), originalIndex)
      result.add(node)
      currentIndex = nonBlankLines.indexOfFirst { it.index >= nextIndex }.takeIf { it != -1 } ?: nonBlankLines.size
    }

    return result
  }

  fun isRootLine(line: String): Boolean {
    val first = line.firstOrNull() ?: return false
    return first !in INDENT_PREFIXES
  }

  private fun parseLevel(lines: List<String>, startIndent: Int, index: Int): ParseResult {
    val line = lines[index]
    val name = extractPackageName(line)
    val group = extractGroup(line)
    val version = extractVersion(line)
    val node = PackageTreeNode(PyPackageName.from(name), mutableListOf(), group, version)
    var currentIndex = index + 1
    while (currentIndex < lines.size && calculateIndentLevel(lines[currentIndex]) > startIndent) {
      val result = parseLevel(lines, calculateIndentLevel(lines[currentIndex]), currentIndex)
      node.children.add(result.node)
      currentIndex = result.nextIndex
    }
    return ParseResult(node, currentIndex)
  }

  private fun calculateIndentLevel(line: String): Int {
    val indentMatch = TREE_LINE_REGEX.find(line)?.value ?: ""
    return indentMatch.length / 4
  }

  private fun extractPackageName(line: String): String {
    val clean = line.replaceFirst(TREE_LINE_REGEX, "").trimStart()
    return clean.split(SPACE_DELIMITER, limit = 2)[0]
      .substringBefore(VERSION_DELIMITER)
  }

  private fun extractGroup(line: String): String? {
    val groupMatch = GROUP_REGEX.find(line)
    return groupMatch?.groupValues?.get(1)
  }

  private fun extractVersion(line: String): String? {
    val clean = line.replaceFirst(TREE_LINE_REGEX, "").trimStart()
    // Skip the package name and optional bracketed extras (e.g., "pkg[cli, nats] v1.0")
    val afterName = clean.let {
      val bracketStart = it.indexOf(VERSION_DELIMITER)
      if (bracketStart >= 0) {
        val bracketEnd = it.indexOf(']', bracketStart)
        if (bracketEnd >= 0) it.substring(bracketEnd + 1).trimStart() else it.substringAfter(SPACE_DELIMITER, "")
      }
      else {
        it.substringAfter(SPACE_DELIMITER, "")
      }
    }
    return afterName
      .removePrefix("v")
      .substringBefore(' ')
      .takeIf { it.isNotBlank() }
  }
}

/**
 * Extracts declared (depth-1) dependencies from pre-parsed dependency trees.
 * Each root node's direct children represent a declared dependency.
 */
@ApiStatus.Internal
fun extractDeclaredDependencies(trees: List<PackageTreeNode>): List<PythonPackage> {
  return trees.flatMap { root ->
    root.children.map { child ->
      PythonPackage(
        child.name.name,
        child.version ?: "",
        false,
        child.group?.let { PyDependencyGroupName(it) }
      )
    }
  }.distinctBy { it.name }
}

@ApiStatus.Internal
internal interface DependencyTreeProvider {
  suspend fun getDependencyTrees(): List<PackageTreeNode>
  fun invalidateCache()
}

@ApiStatus.Internal
internal class CachedDependencyTreeProvider(
  private val fetchOutput: suspend () -> String?,
) : DependencyTreeProvider {
  private val mutex = Mutex()

  @Volatile
  private var cachedTrees: List<PackageTreeNode>? = null

  override suspend fun getDependencyTrees(): List<PackageTreeNode> {
    cachedTrees?.let { return it }
    return mutex.withLock {
      cachedTrees?.let { return it }
      val output = fetchOutput()
      val trees = if (output != null) TreeParser.parseTrees(output.lines()) else emptyList()
      cachedTrees = trees
      trees
    }
  }

  override fun invalidateCache() {
    cachedTrees = null
  }
}
