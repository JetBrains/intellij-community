package com.intellij.mcpserver.toolsets.general

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
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
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.util.Processor
import com.intellij.util.asDisposable
import com.intellij.util.indexing.FindSymbolParameters
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * Searches for symbols via Choose By Name models and maps them to [SearchItem]s.
 */
internal suspend fun searchSymbols(
  q: String,
  paths: List<String>?,
  limit: Int,
): SearchResult {
  val effectiveLimit = normalizeLimit(limit)
  val project = currentCoroutineContext().project
  val projectDir = project.projectDirectory
  val pathScope = buildPathScope(projectDir, paths)
  val directoryFilterPath = resolveDirectoryFilter(project, pathScope)
  val directoryFilterFile = directoryFilterPath?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
  val searchScope = directoryFilterFile?.let { GlobalSearchScopes.directoryScope(project, it, true) }
                   ?: GlobalSearchScope.projectScope(project)

  val fileDocumentManager = serviceAsync<FileDocumentManager>()
  val provider = DefaultChooseByNameItemProvider(null)
  val items = LinkedHashSet<SearchItem>()
  val requestedCount = (effectiveLimit * SEARCH_SCOPE_MULTIPLIER).coerceAtMost(MAX_RESULTS_UPPER_BOUND)
  var seenCount = 0
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
        val viewModel = SimpleChooseByNameViewModel(project, model, requestedCount)
        val transformedPattern = viewModel.transformPattern(q)
        if (transformedPattern.isBlank()) continue
        val localPattern = computeLocalPattern(model, transformedPattern)
        val params = FindSymbolParameters.wrap(transformedPattern, searchScope)
          .withLocalPattern(localPattern)

        val completed = readAction {
          blockingContextToIndicator {
            val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
            provider.filterElementsWithWeights(viewModel, params, indicator, Processor { descriptor: FoundItemDescriptor<*> ->
              indicator.checkCanceled()
              seenCount++
              val navigationItem = descriptor.item as? NavigationItem ?: return@Processor seenCount < requestedCount
              val searchItem = mapNavigationItem(
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
              return@Processor seenCount < requestedCount
            })
          }
        }

        if (!completed || reachedLimit || seenCount >= requestedCount) break
      }
    }
  } == null

  return SearchResult(
    items = items.toList(),
    more = timedOut || reachedLimit || seenCount >= requestedCount,
  )
}

private class SimpleChooseByNameViewModel(
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

private fun computeLocalPattern(model: ChooseByNameModel, pattern: String): String {
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

  val virtualFile = psiElement.containingFile?.virtualFile ?: return null
  val filePath = projectDir.relativizeIfPossible(virtualFile)
  if (filePath.isBlank()) return null
  if (!matchesPathScope(pathScope, projectDir, filePath)) return null

  val snippet = buildPsiSnippet(projectDir, fileDocumentManager, psiElement)
  return SearchItem(
    filePath = filePath,
    startLine = snippet?.startLine,
    startColumn = snippet?.startColumn,
    endLine = snippet?.endLine,
    endColumn = snippet?.endColumn,
    startOffset = snippet?.startOffset,
    endOffset = snippet?.endOffset,
    lineText = snippet?.lineText,
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

private fun buildPsiSnippet(
  projectDir: Path,
  fileDocumentManager: FileDocumentManager,
  element: PsiElement,
): UsageSnippet? {
  val navigationElement = element.navigationElement ?: element
  val file = navigationElement.containingFile?.virtualFile ?: return null
  val textRange = navigationElement.textRange
  return buildSnippet(projectDir, fileDocumentManager, file, textRange)
}

private fun buildSnippet(
  projectDir: Path,
  fileDocumentManager: FileDocumentManager,
  file: VirtualFile,
  textRange: Segment,
): UsageSnippet? {
  val document = fileDocumentManager.getDocument(file) ?: return null
  val snippet = buildSearchSnippet(document = document, textRange = textRange, maxTextChars = Constants.MAX_USAGE_TEXT_CHARS)
  return UsageSnippet(
    file = file,
    filePath = projectDir.relativizeIfPossible(file),
    lineText = snippet.lineText,
    startLine = snippet.startLine,
    startColumn = snippet.startColumn,
    endLine = snippet.endLine,
    endColumn = snippet.endColumn,
    startOffset = snippet.startOffset,
    endOffset = snippet.endOffset,
  )
}

private data class UsageSnippet(
  @JvmField val file: VirtualFile,
  @JvmField val filePath: String,
  @JvmField val lineText: String,
  @JvmField val startLine: Int,
  @JvmField val startColumn: Int,
  @JvmField val endLine: Int,
  @JvmField val endColumn: Int,
  @JvmField val startOffset: Int,
  @JvmField val endOffset: Int,
)
