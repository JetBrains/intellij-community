// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.markdown.linkgraph

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.isMarkdownType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownLinkGraphService(
  private val project: Project,
  private val maxElements: Int = MAX_ELEMENTS,
) {
  fun buildGraph(): MarkdownGraphDto {
    val nodes = LinkedHashMap<String, MarkdownGraphNodeDto>()
    val edges = ArrayList<MarkdownGraphEdgeDto>()
    val markdownFiles = markdownFiles()
    val graphRoot = graphRootFor(markdownFiles)

    for (sourceFile in markdownFiles) {
      val sourceNodeId = ensureFileNode(sourceFile, nodes, graphRoot)
      for (destination in getOutgoingMarkdownLinkDestinationTexts(project, sourceFile)) {
        val targetFile = resolveMarkdownTarget(sourceFile, destination, graphRoot) ?: continue
        val targetNodeId = ensureFileNode(targetFile, nodes, graphRoot)
        edges.add(MarkdownGraphEdgeDto("edge:${edges.size}", sourceNodeId, targetNodeId))
      }
    }

    return limitGraph(nodes.values.toList(), edges)
  }

  fun findFileById(fileId: String): VirtualFile? {
    val relativePath = fileId.removePrefix(FILE_ID_PREFIX)
    val markdownFiles = markdownFiles()
    val graphRoot = graphRootFor(markdownFiles)
    return markdownFiles.firstOrNull { projectRelativePath(it, graphRoot) == relativePath }
  }

  private fun markdownFiles(): List<VirtualFile> {
    return FileTypeIndex.getFiles(MarkdownFileType.INSTANCE, GlobalSearchScope.projectScope(project))
      .asSequence()
      .filter(::isMarkdownGraphFile)
      .sortedBy { it.path }
      .toList()
  }

  private fun ensureFileNode(file: VirtualFile, nodes: MutableMap<String, MarkdownGraphNodeDto>, graphRoot: VirtualFile?): String {
    val id = fileNodeId(file, graphRoot)
    nodes.getOrPut(id) {
      MarkdownGraphNodeDto(
        id = id,
        label = file.name,
        kind = FILE_KIND,
        path = projectRelativePath(file, graphRoot),
        parent = ensureCompoundParent(file, nodes, graphRoot),
      )
    }
    return id
  }

  private fun ensureCompoundParent(file: VirtualFile, nodes: MutableMap<String, MarkdownGraphNodeDto>, graphRoot: VirtualFile?): String? {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val module = fileIndex.getModuleForFile(file)
    var parentId = module?.let { ensureModuleNode(it, nodes) }
    val root = graphRoot ?: fileIndex.getContentRootForFile(file)
    val folder = file.parent ?: return parentId
    val relativeFolderPath = root?.let { VfsUtilCore.getRelativePath(folder, it, '/') }
      ?.trimStart('/')
      ?.takeIf { it.isNotEmpty() }
      ?: return parentId
    var pathPrefix = ""
    for (segment in relativeFolderPath.split('/')) {
      pathPrefix = if (pathPrefix.isEmpty()) segment else "$pathPrefix/$segment"
      val folderId = folderNodeId(module, pathPrefix)
      nodes.getOrPut(folderId) {
        MarkdownGraphNodeDto(
          id = folderId,
          label = segment,
          kind = FOLDER_KIND,
          parent = parentId,
        )
      }
      parentId = folderId
    }
    return parentId
  }

  private fun ensureModuleNode(module: Module, nodes: MutableMap<String, MarkdownGraphNodeDto>): String {
    val moduleId = moduleNodeId(module)
    nodes.getOrPut(moduleId) {
      MarkdownGraphNodeDto(
        id = moduleId,
        label = module.name,
        kind = MODULE_KIND,
      )
    }
    return moduleId
  }

  private fun resolveMarkdownTarget(sourceFile: VirtualFile, rawDestination: String, graphRoot: VirtualFile?): VirtualFile? {
    val localPath = normalizeLocalPath(rawDestination) ?: return null
    val candidatePath = localPath.trimStart('/')
    val baseDirectories = if (localPath.startsWith('/')) {
      listOfNotNull(graphRoot)
    }
    else {
      candidateBaseDirectories(sourceFile.parent, graphRoot)
    }

    for (baseDirectory in baseDirectories) {
      val target = findMarkdownFile(baseDirectory, candidatePath)
      if (target != null) {
        return target
      }
    }
    return null
  }

  private fun candidateBaseDirectories(startDirectory: VirtualFile?, graphRoot: VirtualFile?): List<VirtualFile> {
    val result = ArrayList<VirtualFile>()
    var directory = startDirectory
    while (directory != null) {
      if (graphRoot != null && !VfsUtilCore.isAncestor(graphRoot, directory, false)) {
        break
      }
      result.add(directory)
      if (graphRoot != null && directory == graphRoot) {
        break
      }
      directory = directory.parent
    }
    if (result.isEmpty() && graphRoot != null) {
      result.add(graphRoot)
    }
    return result
  }

  private fun findMarkdownFile(baseDirectory: VirtualFile, relativePath: String): VirtualFile? {
    val target = baseDirectory.findFileByRelativePath(relativePath)
    if (target != null && isMarkdownGraphFile(target)) {
      return target
    }
    if (hasFileExtension(relativePath)) {
      return null
    }
    val markdownTarget = baseDirectory.findFileByRelativePath("$relativePath.md")
    return markdownTarget?.takeIf(::isMarkdownGraphFile)
  }

  private fun isMarkdownGraphFile(file: VirtualFile): Boolean {
    return file.isValid &&
           file.fileType.isMarkdownType() &&
           ProjectRootManager.getInstance(project).fileIndex.isInContent(file) &&
           !isUnderGeneratedOutputDirectory(file)
  }

  private fun isUnderGeneratedOutputDirectory(file: VirtualFile): Boolean {
    var current = file.parent
    while (current != null) {
      if (current.name in GENERATED_OUTPUT_DIRECTORY_NAMES) {
        return true
      }
      current = current.parent
    }
    return false
  }

  private fun normalizeLocalPath(rawDestination: String): String? {
    val path = rawDestination
      .substringBefore('#')
      .substringBefore('?')
      .trim()
    if (path.isEmpty() || path.startsWith('#') || path.startsWith("//")) {
      return null
    }
    if (SCHEME_PATTERN.matches(path)) {
      return null
    }
    return path
  }

  private fun limitGraph(nodes: List<MarkdownGraphNodeDto>, edges: List<MarkdownGraphEdgeDto>): MarkdownGraphDto {
    val truncated = nodes.size + edges.size > maxElements
    if (!truncated) {
      return MarkdownGraphDto(nodes, edges, truncated = false)
    }

    val visibleNodes = nodes.take(maxElements)
    val visibleNodeIds = visibleNodes.mapTo(HashSet()) { it.id }
    val remainingEdgeBudget = maxElements - visibleNodes.size
    val visibleEdges = if (remainingEdgeBudget > 0) {
      edges
        .asSequence()
        .filter { it.source in visibleNodeIds && it.target in visibleNodeIds }
        .take(remainingEdgeBudget)
        .toList()
    }
    else {
      emptyList()
    }
    return MarkdownGraphDto(visibleNodes, visibleEdges, truncated = true)
  }

  private fun graphRootFor(files: Collection<VirtualFile>): VirtualFile? {
    val projectBase = projectBaseDir()
    if (projectBase != null && files.all { it == projectBase || VfsUtilCore.isAncestor(projectBase, it, false) }) {
      return projectBase
    }
    return commonAncestor(files)
  }

  @Suppress("DEPRECATION")
  private fun projectBaseDir(): VirtualFile? = project.baseDir

  private fun commonAncestor(files: Collection<VirtualFile>): VirtualFile? {
    var ancestor = files.firstOrNull()?.parent ?: return null
    for (file in files.drop(1)) {
      while (ancestor.parent != null && !VfsUtilCore.isAncestor(ancestor, file, false) && ancestor != file) {
        ancestor = ancestor.parent
      }
    }
    return ancestor
  }

  private fun projectRelativePath(file: VirtualFile, graphRoot: VirtualFile?): String {
    return graphRoot
      ?.let { VfsUtilCore.getRelativePath(file, it, '/') }
      ?.trimStart('/')
      ?: file.path
  }

  private fun fileNodeId(file: VirtualFile, graphRoot: VirtualFile?): String = "$FILE_ID_PREFIX${projectRelativePath(file, graphRoot)}"

  private fun moduleNodeId(module: Module): String = "module:${module.name}"

  private fun folderNodeId(module: Module?, relativePath: String): String {
    return "folder:${module?.name ?: "project"}:$relativePath"
  }

  private fun hasFileExtension(path: String): Boolean {
    return path.substringAfterLast('/').contains('.')
  }

  companion object {
    const val MAX_ELEMENTS: Int = 4000
    private const val FILE_KIND: String = "file"
    private const val FOLDER_KIND: String = "folder"
    private const val MODULE_KIND: String = "module"
    private const val FILE_ID_PREFIX: String = "file:"
    private val SCHEME_PATTERN = Regex("[A-Za-z][A-Za-z0-9+.-]*:.*")
    private val GENERATED_OUTPUT_DIRECTORY_NAMES = setOf(
      ".git",
      ".gradle",
      ".idea",
      ".venv",
      "build",
      "dist",
      "node_modules",
      "out",
      "target",
      "venv",
    )
  }
}
