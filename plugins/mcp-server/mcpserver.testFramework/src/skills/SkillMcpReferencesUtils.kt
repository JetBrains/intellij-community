// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework.skills

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsMarkdownExporter
import com.intellij.mcpserver.skills.BundledSkill
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.eel.fs.EelFiles
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

/**
 * See [assertUpToDate] and [regenerate]
 */
@ApiStatus.Internal
object SkillMcpReferencesUtils {
  const val REFERENCE_DIR: String = "reference"

  data class Config(
    /** The working-copy directory of the skill, the parent of its `reference/` tree. */
    val skillDirectory: Path,
    val skill: BundledSkill,
    /** Deduplicated by descriptor name. Passed by value: the caller assembles it once for every config. */
    val availableTools: List<McpTool>,
  ) {
    val referenceDir: Path
      get() = skillDirectory.resolve(REFERENCE_DIR)
  }

  /**
   * Compares committed `reference/` files with docs generated from live MCP tool descriptors.
   */
  fun assertUpToDate(config: Config) {
    check(Files.isDirectory(config.referenceDir)) {
      "Bundled ${config.skill.skillId} router reference directory does not exist: ${config.referenceDir}"
    }
    val committedTree = normalizeReferenceTree(readGeneratedReferenceTree(config.referenceDir))
    val generatedTree = normalizeReferenceTree(generateReference(config).tree)
    if (committedTree != generatedTree) {
      throw AssertionError("""
        Bundled ${config.skill.skillId} router reference is out of date.
        Run the main function next to the test to refresh the committed golden.
        """.trimIndent())
    }
  }

  /**
   * Rewrites committed `reference/` files from live MCP tool descriptors.
   */
  fun regenerate(config: Config) {
    val generated = generateReference(config)
    Files.deleteIfExists(config.referenceDir.resolve(McpToolsMarkdownExporter.TREE_INDEX_FILE))
    val toolsDir = config.referenceDir.resolve(McpToolsMarkdownExporter.TREE_TOOLS_SUBDIR)
    if (Files.exists(toolsDir)) {
      EelFileUtils.deleteRecursively(toolsDir)
    }
    for ((relativePath, content) in generated.tree) {
      val target = config.referenceDir.resolve(relativePath)
      Files.createDirectories(target.parent)
      Files.writeString(target, content)
    }

    println("${config.skill.skillId}: ${generated.tools.size} tools, ${generated.tree.size} files -> ${config.referenceDir}")
  }

  private fun readGeneratedReferenceTree(root: Path): Map<String, String> {
    return Files.walk(root).use { stream ->
      stream
        .filter(Files::isRegularFile)
        .toList()
        .associate { file ->
          file.relativeTo(root).invariantSeparatorsPathString to EelFiles.readString(file, Charsets.UTF_8)
        }
        .filterKeys(::isGeneratedReferencePath)
        .toSortedMap()
    }
  }

  private fun isGeneratedReferencePath(path: String): Boolean {
    return path == McpToolsMarkdownExporter.TREE_INDEX_FILE ||
           path.startsWith("${McpToolsMarkdownExporter.TREE_TOOLS_SUBDIR}/")
  }

  private fun normalizeReferenceTree(tree: Map<String, String>): Map<String, String> {
    return tree.mapValues { (_, content) -> content.trimEnd() }
  }

  private data class GeneratedReference(
    val tools: List<McpTool>,
    val tree: Map<String, String>,
  )

  private fun generateReference(config: Config): GeneratedReference {
    val tools = config.availableTools
      .filter { it.descriptor.name in config.skill.tools }
      .sortedBy { it.descriptor.name.lowercase() }
    val toolNames = tools.mapTo(mutableSetOf()) { it.descriptor.name }
    val missingTools = config.skill.tools - toolNames
    check(missingTools.isEmpty()) {
      "Failed to generate router reference for '${config.skill.skillId}'. Missing live tools: ${missingTools.sorted()}."
    }
    val tree = McpToolsMarkdownExporter.generateMarkdownTree(tools).toSortedMap()
    validateGeneratedTree(config.skill.skillId, config.skill.tools, tree.keys)
    return GeneratedReference(tools, tree)
  }

  private fun validateGeneratedTree(skillId: String, availableToolNames: Set<String>, paths: Set<String>) {
    val expectedPaths = buildSet {
      add(McpToolsMarkdownExporter.TREE_INDEX_FILE)
      availableToolNames.forEach { add("${McpToolsMarkdownExporter.TREE_TOOLS_SUBDIR}/$it.md") }
    }
    check(paths == expectedPaths) {
      "Generated $skillId router reference uses unexpected paths. Expected ${expectedPaths.sorted()}, got ${paths.sorted()}."
    }
  }
}
