@file:Suppress("DuplicatedCode")

package com.intellij.mcpserver.toolsets.general

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.EditSourceUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.ContributorsBasedGotoByModel
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.project
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Processor
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.FindSymbolParameters
import org.jetbrains.annotations.ApiStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * Searches for symbols via Choose By Name models and maps them to [SearchItem]s.
 */
@ApiStatus.Internal
suspend fun searchSymbols(
  q: String,
  paths: List<String>?,
  includeExternal: Boolean,
  limit: Int,
): SearchResult {
  val effectiveLimit = normalizeLimit(limit)
  val project = currentCoroutineContext().project
  val projectDir = project.projectDirectory
  val pathScope = buildPathScope(projectDir, paths)
  val directoryFilterPath = resolveDirectoryFilter(project, pathScope)
  val directoryFilterFile = directoryFilterPath?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
  val baseSearchScope = directoryFilterFile?.let { GlobalSearchScopes.directoryScope(project, it, true) }
                        ?: if (includeExternal) GlobalSearchScope.allScope(project)
                        else GlobalSearchScope.projectScope(project)
  val searchScope = pathScope?.let { PathFilteredGlobalSearchScope(baseSearchScope, projectDir, it) } ?: baseSearchScope

  val fileDocumentManager = serviceAsync<FileDocumentManager>()
  val provider = DefaultChooseByNameItemProvider(null)
  val items = LinkedHashSet<SearchItem>()
  val requestedCount = (effectiveLimit * SEARCH_SCOPE_MULTIPLIER).coerceAtMost(MAX_RESULTS_UPPER_BOUND)
  var providerStoppedEarly = false
  var reachedLimit = false

  val timedOut = withTimeoutOrNull(Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE.milliseconds) {
    withBackgroundProgress(
      project = project,
      title = McpServerBundle.message("tool.activity.searching.files.for.text", q),
      cancellable = true,
    ) {
      val parentDisposable = asDisposable()
      val classModel = GotoClassModel2(project).also { Disposer.register(parentDisposable, it) }
      val symbolModel = GotoSymbolModel2(project, parentDisposable).also { Disposer.register(parentDisposable, it) }
      val models = listOf(classModel, symbolModel)
      for (model in models) {
        val viewModel = McpChooseByNameViewModel(project, model, requestedCount)
        val transformedPattern = viewModel.transformPattern(q)
        if (transformedPattern.isBlank()) continue
        val localPattern = computeChooseByNameLocalPattern(model, transformedPattern)
        val params = FindSymbolParameters.wrap(transformedPattern, searchScope)
          .withLocalPattern(localPattern)

        val completed = readAction {
          blockingContextToIndicator {
            val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
            provider.filterElementsWithWeights(viewModel, params, indicator, Processor { descriptor: FoundItemDescriptor<*> ->
              indicator.checkCanceled()
              val navigationItem = descriptor.item as? NavigationItem ?: return@Processor true
              val searchItem = mapNavigationItem(
                project = project,
                item = navigationItem,
                projectDir = projectDir,
                fileDocumentManager = fileDocumentManager,
                pathScope = pathScope,
              )
              if (searchItem != null) {
                items.add(searchItem)
                if (items.size >= effectiveLimit) {
                  reachedLimit = true
                  return@Processor false
                }
              }
              return@Processor true
            })
          }
        }

        if (!completed) {
          providerStoppedEarly = true
        }
        if (!completed || reachedLimit) break
      }
    }
  } == null

  return SearchResult(
    items = items.toList(),
    more = timedOut || reachedLimit || providerStoppedEarly,
  )
}

private class PathFilteredGlobalSearchScope(
  baseScope: GlobalSearchScope,
  private val projectDir: Path,
  private val pathScope: PathScope,
) : DelegatingGlobalSearchScope(baseScope) {
  @Suppress("RedundantIf")
  override fun contains(file: VirtualFile): Boolean {
    if (!super.contains(file)) return false
    val filePath = file.toNioPathOrNull() ?: return false
    val relativePath = try {
      projectDir.relativize(filePath)
    }
    catch (_: IllegalArgumentException) {
      return false
    }
    if (relativePath.nameCount > 0 && relativePath.getName(0).toString() == "..") return false
    return pathScope.matches(relativePath)
  }
}

internal class McpChooseByNameViewModel(
  private val project: Project,
  private val model: ChooseByNameModel,
  private val maximumListSizeLimit: Int,
) : ChooseByNameViewModel {
  override fun getProject(): Project = project

  override fun getModel(): ChooseByNameModel = model

  override fun isSearchInAnyPlace(): Boolean = true

  override fun transformPattern(pattern: String): String {
    return if (model is ContributorsBasedGotoByModel) model.removeModelSpecificMarkup(pattern) else pattern
  }

  override fun canShowListForEmptyPattern(): Boolean = false

  override fun getMaximumListSizeLimit(): Int = maximumListSizeLimit
}

internal fun computeChooseByNameLocalPattern(model: ChooseByNameModel, pattern: String): String {
  var lastSeparatorOccurrence = 0
  for (separator in model.separators) {
    var idx = pattern.lastIndexOf(separator)
    if (idx == pattern.length - 1) {
      idx = pattern.lastIndexOf(separator, idx - 1)
    }
    lastSeparatorOccurrence = maxOf(lastSeparatorOccurrence, if (idx == -1) idx else idx + separator.length)
  }
  return pattern.substring(lastSeparatorOccurrence)
}

private fun mapNavigationItem(
  project: Project,
  item: NavigationItem,
  projectDir: Path,
  fileDocumentManager: FileDocumentManager,
  pathScope: PathScope?,
): SearchItem? {
  val psiElement = when (item) {
                     is PsiElement -> item
                     is PsiElementNavigationItem -> item.targetElement
                     else -> null
                   } ?: return null

  val anchor = resolveNavigationAnchor(psiElement) ?: return null
  val filePath = projectDir.relativizeIfPossible(anchor.file)
  if (filePath.isBlank()) return null
  if (!matchesPathScope(pathScope, projectDir, filePath)) return null

  val snippet = buildSnippet(project, fileDocumentManager, anchor)
  return SearchItem(
    filePath = filePath,
    startLine = snippet?.startLine,
    startColumn = snippet?.startColumn,
    endLine = snippet?.endLine,
    endColumn = snippet?.endColumn,
  )
}

private fun matchesPathScope(pathScope: PathScope?, projectDir: Path, filePath: String): Boolean {
  if (pathScope == null) return true
  val relativePath = toRelativePath(projectDir, filePath) ?: return false
  return pathScope.matches(relativePath)
}

private fun toRelativePath(projectDir: Path, filePath: String): Path? {
  if (filePath.isBlank()) return null
  val nioPath = runCatching { Path.of(filePath) }.getOrNull() ?: return null
  if (nioPath.isAbsolute) {
    return if (nioPath.startsWith(projectDir)) projectDir.relativize(nioPath) else null
  }
  return nioPath
}

@RequiresReadLock
private fun buildSnippet(
  project: Project,
  fileDocumentManager: FileDocumentManager,
  anchor: NavigationAnchor,
): SearchSnippet? {
  if (anchor.file.fileType.isBinary) {
    return null
  }
  val document = fileDocumentManager.getDocument(anchor.file, project) ?: return null
  val textRange = resolveSnippetRange(anchor, document)
  return buildSearchSnippet(document = document, textRange = textRange)
}

private data class NavigationAnchor(
  @JvmField val element: PsiElement,
  @JvmField val file: VirtualFile,
  @JvmField val offset: Int,
)

@RequiresReadLock
private fun resolveNavigationAnchor(element: PsiElement): NavigationAnchor? {
  val originalElement = EditSourceUtil.getNavigatableOriginalElement(element) ?: element
  val navigationElement = originalElement.navigationElement ?: originalElement
  val gotoTarget = TargetElementUtil.getInstance().getGotoDeclarationTarget(originalElement, navigationElement) ?: navigationElement
  val descriptor = PsiNavigationSupport.getInstance().getDescriptor(gotoTarget)
  if (descriptor is OpenFileDescriptor) {
    val file = descriptor.file
    if (file.isValid) {
      return NavigationAnchor(gotoTarget, file, descriptor.offset)
    }
  }
  val file = PsiUtilCore.getVirtualFile(gotoTarget) ?: return null
  if (!file.isValid) return null
  return NavigationAnchor(gotoTarget, file, gotoTarget.textOffset)
}

private fun resolveSnippetRange(anchor: NavigationAnchor, document: com.intellij.openapi.editor.Document): Segment {
  val anchorElementFile = anchor.element.containingFile?.virtualFile
  val elementRange = anchor.element.textRange
  if (anchorElementFile == anchor.file && elementRange != null && elementRange.endOffset <= document.textLength) {
    return elementRange
  }
  if (document.textLength == 0) {
    return TextRange(0, 0)
  }
  val startOffset = when {
    anchor.offset < 0 -> 0
    anchor.offset >= document.textLength -> document.textLength - 1
    else -> anchor.offset
  }
  val endOffset = minOf(document.textLength, startOffset + 1)
  return TextRange(startOffset, endOffset)
}
