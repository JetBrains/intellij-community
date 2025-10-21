// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.packageRequirements

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PythonPackageRequirementsTreeExtractor {
  suspend fun extract(pkg: PythonPackage): PackageNode

  companion object {
    private val treeParser = TreeParser()

    fun forSdk(sdk: Sdk): PythonPackageRequirementsTreeExtractor? =
      PythonPackageRequirementsTreeExtractorProvider.EP_NAME.extensionList
        .firstNotNullOfOrNull { it.createExtractor(sdk) }

    fun parseTree(lines: List<String>): PackageNode = treeParser.parseTree(lines)
  }
}

@ApiStatus.Internal
interface PythonPackageRequirementsTreeExtractorProvider {
  fun createExtractor(sdk: Sdk): PythonPackageRequirementsTreeExtractor?

  companion object {
    private const val EP_NAME_VALUE = "Pythonid.PythonPackageRequirementsTreeExtractorProvider"
    val EP_NAME: ExtensionPointName<PythonPackageRequirementsTreeExtractorProvider> =
      ExtensionPointName.create(EP_NAME_VALUE)
  }
}

@ApiStatus.Internal
data class PackageNode(
  val name: PyPackageName,
  val children: MutableList<PackageNode> = mutableListOf(),
)

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
    val node = PackageNode(PyPackageName.from(name))
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
    return indentMatch.chunked(4).count { it.isNotBlank() }
  }

  private fun extractPackageName(line: String): String {
    val clean = line.replaceFirst(TREE_LINE_REGEX, "").trimStart()
    return clean.split(SPACE_DELIMITER, limit = 2)[0]
      .substringBefore(VERSION_DELIMITER)
  }

  companion object Constants {
    private val TREE_LINE_REGEX = Regex("""^[\s│|`]*[├└`|][-─]+ """)
    private const val SPACE_DELIMITER = ' '
    private const val VERSION_DELIMITER = '['
  }
}