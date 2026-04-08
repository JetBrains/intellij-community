// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.FileHyperlinkInfoBase
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.navigateFileHyperlink
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Internal
object TerminalSourceNavigationInfo {
  private val SOURCE_NAVIGATION_PROJECT_PATH_KEY = Key.create<String>("terminal.source.navigation.project.path")

  fun setProjectPath(editor: Editor, projectPath: String?) {
    editor.putUserData(SOURCE_NAVIGATION_PROJECT_PATH_KEY, projectPath)
  }

  fun getProjectPath(mouseEvent: EditorMouseEvent?): String? {
    return mouseEvent?.editor?.getUserData(SOURCE_NAVIGATION_PROJECT_PATH_KEY)?.takeIf { it.isNotBlank() }
  }
}

@Internal
class TerminalCrossProjectFileHyperlinkNavigator(
  private val sourceNavigationProjectPath: (EditorMouseEvent?) -> String? = TerminalSourceNavigationInfo::getProjectPath,
  private val openProject: suspend (String) -> Project? = ::openOrReuseProjectByPath,
  private val focusProjectWindow: suspend (Project) -> Unit = ::focusProjectWindowForNavigation,
  private val navigate: suspend (Project, OpenFileDescriptor, Boolean) -> Boolean = ::navigateDescriptorInProject,
) {
  suspend fun navigate(project: Project, hyperlinkInfo: HyperlinkInfo, mouseEvent: EditorMouseEvent?): Boolean {
    if (project.isDisposed) {
      return false
    }
    val sourceProjectPath = sourceNavigationProjectPath(mouseEvent)?.takeIf { it.isNotBlank() } ?: return false
    val fileHyperlinkInfo = hyperlinkInfo as? FileHyperlinkInfo ?: return false
    val useBrowser = (fileHyperlinkInfo as? FileHyperlinkInfoBase)?.isUseBrowserForNavigation ?: true
    val descriptor = readAction { fileHyperlinkInfo.descriptor } ?: return false
    if (!descriptor.file.isValid) {
      return false
    }

    val targetProject = openProject(sourceProjectPath) ?: return false
    if (targetProject.isDisposed) {
      return false
    }
    val targetDescriptor = buildTargetDescriptor(targetProject, descriptor) ?: return false
    focusProjectWindow(targetProject)
    return navigate(targetProject, targetDescriptor, useBrowser)
  }
}

private fun buildTargetDescriptor(targetProject: Project, sourceDescriptor: OpenFileDescriptor): OpenFileDescriptor? {
  val file = sourceDescriptor.file.takeIf { it.isValid } ?: return null
  val targetDescriptor = when {
    sourceDescriptor.line >= 0 -> OpenFileDescriptor(targetProject, file, sourceDescriptor.line, sourceDescriptor.column)
    sourceDescriptor.offset >= 0 -> OpenFileDescriptor(targetProject, file, sourceDescriptor.offset)
    else -> OpenFileDescriptor(targetProject, file)
  }
  targetDescriptor.setUseCurrentWindow(sourceDescriptor.isUseCurrentWindow)
  targetDescriptor.setUsePreviewTab(sourceDescriptor.isUsePreviewTab)
  return targetDescriptor
}

@Internal
class SourceNavigationProjectRouter<P>(
  private val parsePath: (String) -> Path?,
  private val normalizePath: (String) -> String,
  private val resolveManagedPath: (Path) -> String?,
  private val openProjectsProvider: () -> List<P>,
  private val projectIdentityPath: (P) -> String?,
  private val isPathEquivalent: (P, Path) -> Boolean,
  private val openProjectByPath: suspend (Path, OpenProjectTask) -> P?,
) {
  suspend fun openOrReuseProject(
    path: String,
    options: OpenProjectTask = OpenProjectTask(),
  ): P? {
    val target = resolveTarget(path) ?: return null
    return findOpenProject(target) ?: openProjectByPath(target.managedPath, options)
  }

  private fun findOpenProject(target: ResolvedSourceNavigationProjectPath): P? {
    val openProjects = openProjectsProvider()
    val directMatch = openProjects.firstOrNull { project ->
      projectIdentityPath(project)?.let(normalizePath) == target.managedNormalizedPath
    }
    if (directMatch != null) {
      return directMatch
    }

    return openProjects.firstOrNull { project ->
      isPathEquivalent(project, target.requestedPath)
    }
  }

  private fun resolveTarget(path: String): ResolvedSourceNavigationProjectPath? {
    val requestedNormalizedPath = normalizePath(path)
    val requestedPath = parsePath(requestedNormalizedPath) ?: return null
    val managedNormalizedPath = resolveManagedPath(requestedPath)?.let(normalizePath) ?: requestedNormalizedPath
    val managedPath = parsePath(managedNormalizedPath) ?: requestedPath
    return ResolvedSourceNavigationProjectPath(
      requestedPath = requestedPath,
      managedNormalizedPath = managedNormalizedPath,
      managedPath = managedPath,
    )
  }
}

private data class ResolvedSourceNavigationProjectPath(
  @JvmField val requestedPath: Path,
  @JvmField val managedNormalizedPath: String,
  @JvmField val managedPath: Path,
)

private fun normalizeSourceNavigationProjectPath(path: String): String {
  return runCatching {
    Path.of(path).normalize().invariantSeparatorsPathString
  }.getOrDefault(path)
}

private fun parseSourceNavigationProjectPathOrNull(path: String): Path? {
  return runCatching {
    Path.of(path)
  }.getOrNull()
}

private suspend fun openOrReuseProjectByPath(projectPath: String): Project? {
  val recentProjectsManager = serviceAsync<RecentProjectsManager>() as? RecentProjectsManagerBase ?: return null
  val router = SourceNavigationProjectRouter(
    parsePath = ::parseSourceNavigationProjectPathOrNull,
    normalizePath = ::normalizeSourceNavigationProjectPath,
    resolveManagedPath = { path -> recentProjectsManager.getProjectPath(path) },
    openProjectsProvider = { ProjectManager.getInstance().openProjects.toList() },
    projectIdentityPath = { project -> recentProjectsManager.getProjectPath(project)?.invariantSeparatorsPathString },
    isPathEquivalent = { project, path ->
      runCatching {
        isSameProject(projectFile = path, project = project)
      }.getOrDefault(false)
    },
    openProjectByPath = { path, options -> recentProjectsManager.openProject(path, options) },
  )
  val project = router.openOrReuseProject(projectPath) ?: return null
  if (project.isDisposed) {
    return null
  }
  val future = CompletableDeferred<Project>()
  StartupManager.getInstance(project).runAfterOpened {
    future.complete(project)
  }
  future.join()
  return project.takeUnless { it.isDisposed }
}

private suspend fun navigateDescriptorInProject(project: Project, descriptor: OpenFileDescriptor, useBrowser: Boolean): Boolean {
  return navigateFileHyperlink(project, descriptor, useBrowser)
}

private suspend fun focusProjectWindowForNavigation(project: Project) {
  val projectUtilService = project.serviceAsync<ProjectUtilService>()
  withContext(Dispatchers.UI) {
    projectUtilService.focusProjectWindow()
  }
}
