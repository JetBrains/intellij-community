// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.FileHyperlinkInfoBase
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.navigateFileHyperlink
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Answers the project a file hyperlink of one terminal navigates in.
 *
 * A tab that shows the output of another project's work declares a resolver, and the terminal then asks it at
 * click time. The resolver owns the project itself. The terminal opens no project.
 */
@Internal
fun interface TerminalSourceNavigationProjectResolver {
  /** The project a file hyperlink of this terminal navigates in, or `null` to keep the terminal's own project. */
  suspend fun resolveProject(terminalProject: Project): Project?
}

@Internal
object TerminalSourceNavigationInfo {
  private val SOURCE_NAVIGATION_PROJECT_RESOLVER_KEY =
    Key.create<TerminalSourceNavigationProjectResolver>("terminal.source.navigation.project.resolver")

  fun setResolver(editor: Editor, resolver: TerminalSourceNavigationProjectResolver?) {
    editor.putUserData(SOURCE_NAVIGATION_PROJECT_RESOLVER_KEY, resolver)
  }

  fun getResolver(mouseEvent: EditorMouseEvent?): TerminalSourceNavigationProjectResolver? {
    return mouseEvent?.editor?.getUserData(SOURCE_NAVIGATION_PROJECT_RESOLVER_KEY)
  }
}

@Internal
class TerminalCrossProjectFileHyperlinkNavigator(
  private val sourceNavigationProjectResolver: (EditorMouseEvent?) -> TerminalSourceNavigationProjectResolver? =
    TerminalSourceNavigationInfo::getResolver,
  private val focusProjectWindow: suspend (Project) -> Unit = ::focusProjectWindowForNavigation,
  private val navigate: suspend (Project, OpenFileDescriptor, Boolean) -> Boolean = ::navigateDescriptorInProject,
) {
  suspend fun navigate(project: Project, hyperlinkInfo: HyperlinkInfo, mouseEvent: EditorMouseEvent?): Boolean {
    if (project.isDisposed) {
      return false
    }
    val resolver = sourceNavigationProjectResolver(mouseEvent) ?: return false
    val fileHyperlinkInfo = hyperlinkInfo as? FileHyperlinkInfo ?: return false
    val useBrowser = (fileHyperlinkInfo as? FileHyperlinkInfoBase)?.isUseBrowserForNavigation ?: true
    val descriptor = readAction { fileHyperlinkInfo.descriptor } ?: return false
    if (!descriptor.file.isValid) {
      return false
    }

    val targetProject = resolver.resolveProject(project) ?: return false
    // The terminal's own project needs no reroute: the default navigation already opens the target there.
    if (targetProject === project || targetProject.isDisposed) {
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

private suspend fun navigateDescriptorInProject(project: Project, descriptor: OpenFileDescriptor, useBrowser: Boolean): Boolean {
  return navigateFileHyperlink(project, descriptor, useBrowser)
}

private suspend fun focusProjectWindowForNavigation(project: Project) {
  val projectUtilService = project.serviceAsync<ProjectUtilService>()
  withContext(Dispatchers.UI) {
    projectUtilService.focusProjectWindow()
  }
}
