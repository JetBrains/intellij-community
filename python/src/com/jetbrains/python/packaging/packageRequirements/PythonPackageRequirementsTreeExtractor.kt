// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.packageRequirements

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PythonPackageRequirementsTreeExtractor {
  /**
   * Extracts the complete package tree structure.
   * Returns either a workspace member tree or a flat collection of packages.
   *
   * @param declaredPackageNames Set of declared package names for classification
   * @return Node representing the package structure (WorkspaceMemberNode or PackageCollectionNode)
   */
  suspend fun extract(declaredPackageNames: Set<String>): PackageStructureNode

  companion object {
    private val treeParser = TreeParser()

    fun forSdk(sdk: Sdk, project: Project): PythonPackageRequirementsTreeExtractor? =
      PythonPackageRequirementsTreeExtractorProvider.EP_NAME.extensionList
        .firstNotNullOfOrNull { it.createExtractor(sdk, project) }

    fun parseTree(lines: List<String>): PackageNode = treeParser.parseTree(lines)
  }
}

@ApiStatus.Internal
interface PythonPackageRequirementsTreeExtractorProvider {
  fun createExtractor(sdk: Sdk, project: Project): PythonPackageRequirementsTreeExtractor?

  companion object {
    private const val EP_NAME_VALUE = "Pythonid.PythonPackageRequirementsTreeExtractorProvider"
    val EP_NAME: ExtensionPointName<PythonPackageRequirementsTreeExtractorProvider> =
      ExtensionPointName.create(EP_NAME_VALUE)
  }
}

/**
 * Base type for all package tree nodes.
 */
@ApiStatus.Internal
sealed class PackageStructureNode

/**
 * Represents a single package with its dependencies.
 */
@ApiStatus.Internal
data class PackageNode(
  val name: PyPackageName,
  val children: MutableList<PackageNode> = mutableListOf(),
  val group: String? = null,
) : PackageStructureNode()

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
  var packageTree: PackageNode?,
  val undeclaredPackages: List<PackageNode> = emptyList()
) : PackageStructureNode()

/**
 * Represents a flat collection of packages (non-workspace structure).
 *
 * @property declaredPackages Packages explicitly declared in project dependencies
 * @property undeclaredPackages Packages installed but not declared (transitive or manual)
 */
@ApiStatus.Internal
data class PackageCollectionPackageStructureNode(
  val declaredPackages: List<PackageNode>,
  val undeclaredPackages: List<PackageNode>
) : PackageStructureNode()

@ApiStatus.Internal
class TreeParser {
  private data class ParseResult(
    val node: PackageNode,
    val nextIndex: Int,
  )

  fun parseTree(lines: List<String>): PackageNode {
    val (node, _) = parseLevel(lines, calculateIndentLevel(lines.first()), 0)
    return node
  }

  private fun parseLevel(lines: List<String>, startIndent: Int, index: Int): ParseResult {
    val line = lines[index]
    val name = extractPackageName(line)
    val group = extractGroup(line)
    val node = PackageNode(PyPackageName.from(name), mutableListOf(), group)
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

  companion object {
    // Box-drawing characters used in tree output from package managers (uv, poetry, pip)
    private const val VERTICAL = '│'
    private const val BRANCH = '├'
    private const val CORNER = '└'
    private const val HORIZONTAL = '─'
    // ASCII fallbacks some tools use
    private const val VERTICAL_ASCII = '|'
    private const val CORNER_ASCII = '`'
    private const val HORIZONTAL_ASCII = '-'

    private val INDENT_PREFIXES = charArrayOf(' ', VERTICAL, BRANCH, CORNER)

    fun isRootLine(line: String): Boolean {
      val first = line.firstOrNull() ?: return false
      return first !in INDENT_PREFIXES
    }

    private val TREE_LINE_REGEX = Regex(
      """^[\s${VERTICAL}${VERTICAL_ASCII}${CORNER_ASCII}]*[${BRANCH}${CORNER}${CORNER_ASCII}${VERTICAL_ASCII}][${HORIZONTAL_ASCII}${HORIZONTAL}]+ """
    )
    private val GROUP_REGEX = Regex("""\(group:\s*(\w+)\)""")
    private const val SPACE_DELIMITER = ' '
    private const val VERSION_DELIMITER = '['
  }
}