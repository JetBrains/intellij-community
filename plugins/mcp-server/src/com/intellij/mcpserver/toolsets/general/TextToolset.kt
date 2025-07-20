@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.toolsets.Constants.MAX_USAGE_TEXT_CHARS
import com.intellij.mcpserver.util.*
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.Processor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

class TextToolset : McpToolset {
  @McpTool
  @McpDescription("""
        Retrieves the text content of a file using its path relative to project root.
        Use this tool to read file contents when you have the file's project-relative path.
        In the case of binary files, the tool returns an error.
        If the file is too large, the text will be truncated with '<<<...content truncated...>>>' marker and in according to the `truncateMode` parameter.
    """)
  suspend fun get_file_text_by_path(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    pathInProject: String,
    @McpDescription("How to truncate the text: from the start, in the middle, at the end, or don't truncate at all")
    truncateMode: TruncateMode = TruncateMode.START,
    @McpDescription("Max number of lines to return. Truncation will be performed depending on truncateMode.")
    maxLinesCount: Int = 1000,
  ): String {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.reading.file", pathInProject))
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(pathInProject)

    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
               ?: mcpFail("File $resolvedPath doesn't exist or can't be opened")
    val originalText = readAction {
      if (file.fileType.isBinary) mcpFail("File $resolvedPath is binary")
      file.readText()
    }

    return truncateText(originalText, maxLinesCount, maxTextLength, truncateMode, truncatedMarker)
  }

  @McpTool
  @McpDescription("""
        Replaces text in a file with flexible options for find and replace operations.
        Use this tool to make targeted changes without replacing the entire file content.
        This is the most efficient tool for file modifications when you know the exact text to replace.
        
        Requires three parameters:
        - pathInProject: The path to the target file, relative to project root
        - oldTextOrPatte: The text to be replaced (exact match by default)
        - newText: The replacement text
        
        Optional parameters:
        - replaceAll: Whether to replace all occurrences (default: true)
        - caseSensitive: Whether the search is case-sensitive (default: true)
        - regex: Whether to treat oldText as a regular expression (default: false)
        
        Returns one of these responses:
        - "ok" when replacement happened
        - error "project dir not found" if project directory cannot be determined
        - error "file not found" if the file doesn't exist
        - error "could not get document" if the file content cannot be accessed
        - error "no occurrences found" if the old text was not found in the file
        
        Note: Automatically saves the file after modification
    """)
  suspend fun replace_text_in_file(
    @McpDescription("Path to target file relative to project root")
    pathInProject: String,
    @McpDescription("Text to be replaced")
    oldText: String,
    @McpDescription("Replacement text")
    newText: String,
    @McpDescription("Replace all occurrences")
    replaceAll: Boolean = true,
    @McpDescription("Case-sensitive search")
    caseSensitive: Boolean = true,
  ) {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.replacing.text.in.file", pathInProject, oldText, newText))
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(pathInProject)
    val file: VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
                            ?: mcpFail("file not found: $pathInProject")
    val (document, text) = readAction {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: mcpFail("Could not get document for $file")
      document to document.text
    }

    val rangeMarkers = mutableListOf<RangeMarker>()
    var currentStartIndex = 0
    while (true) {
      val occurrenceStart = text.indexOf(oldText, currentStartIndex, !caseSensitive)
      if (occurrenceStart < 0) break
      val rangeMarker = document.createRangeMarker(occurrenceStart, occurrenceStart + oldText.length, true)
      rangeMarkers.add(rangeMarker)
      if (!replaceAll) break // only the first occurence
      currentStartIndex = occurrenceStart + oldText.length
    }

    writeCommandAction(project, commandName = FindBundle.message("find.replace.text.dialog.title")) {
      for (marker in rangeMarkers.reversed()) {
        if (!marker.isValid) continue
        val textRange = marker.textRange
        document.replaceString(textRange.startOffset, textRange.endOffset, newText)
        marker.dispose()
      }
      FileDocumentManager.getInstance().saveDocument(document)
    }
  }

  @McpTool
  @McpDescription("""
        |Searches for a text substring within all files in the project using IntelliJ's search engine.
        |Prefer this tool over reading files with command-line tools because it's much faster.
        |
        |The result occurrences are surrounded with `||` characters, e.g. `some text ||substring|| text`
    """)
  suspend fun search_in_files_by_text(
    @McpDescription("Text substring to search for")
    searchText: String,
    @McpDescription("Directory to search in, relative to project root. If not specified, searches in the entire project.")
    directoryToSearch: String? = null,
    @McpDescription("File mask to search for. If not specified, searches for all files. Example: `*.java`")
    fileMask: String? = null,
    @McpDescription("Whether to search for the text in a case-sensitive manner")
    caseSensitive: Boolean = true,
    @McpDescription("Maximum number of entries to return.")
    maxUsageCount: Int = 1000,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): UsageInfoResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.searching.files.for.text", searchText))
    return search_in_files(searchText, false, directoryToSearch, fileMask, caseSensitive, maxUsageCount, timeout)
  }

  @McpTool
  @McpDescription("""
        |Searches with a regex pattern within all files in the project using IntelliJ's search engine.
        |Prefer this tool over reading files with command-line tools because it's much faster.
        |
        |The result occurrences are surrounded with || characters, e.g. `some text ||substring|| text`
    """)
  suspend fun search_in_files_by_regex(
    @McpDescription("Regex patter to search for")
    regexPattern: String,
    @McpDescription("Directory to search in, relative to project root. If not specified, searches in the entire project.")
    directoryToSearch: String? = null,
    @McpDescription("File mask to search for. If not specified, searches for all files. Example: `*.java`")
    fileMask: String? = null,
    @McpDescription("Whether to search for the text in a case-sensitive manner")
    caseSensitive: Boolean = true,
    @McpDescription("Maximum number of entries to return.")
    maxUsageCount: Int = 1000,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): UsageInfoResult {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.searching.content.with.regex", regexPattern))
    return search_in_files(regexPattern, true, directoryToSearch, fileMask, caseSensitive, maxUsageCount, timeout)
  }

  private suspend fun search_in_files(
    searchTextOrRegex: String,
    isRegex: Boolean,
    directoryToSearch: String? = null,
    fileMask: String? = null,
    caseSensitive: Boolean = true,
    maxUsageCount: Int = 1000,
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): UsageInfoResult {
    val project = currentCoroutineContext().project
    val projectDir = project.projectDirectory

    if (searchTextOrRegex.isBlank()) mcpFail("Search text is empty")

    val findModel = FindManager.getInstance(project).findInProjectModel.clone().apply {
      stringToFind = searchTextOrRegex
      isCaseSensitive = false
      isWholeWordsOnly = false
      isRegularExpressions = false
      isProjectScope = true
      isSearchInProjectFiles = false
      fileFilter = fileMask
      isCaseSensitive = caseSensitive
      isRegularExpressions = isRegex
      if (directoryToSearch != null) {
        val directoryToSearchPath = project.resolveInProject(directoryToSearch)
        if (!directoryToSearchPath.isDirectory()) mcpFail("The specified path '$directoryToSearch' is not a directory.")
        directoryName = directoryToSearchPath.pathString
      }
    }

    val usages = CopyOnWriteArrayList<UsageInfo>()

    val timedOut = withTimeoutOrNull(timeout = timeout.milliseconds) {
      val processor = Processor<UsageInfo> { usageInfo ->
        usages.add(usageInfo)
        return@Processor usages.size < maxUsageCount
      }

      withBackgroundProgress(project, FindBundle.message("find.searching.for.string.in.file.progress", searchTextOrRegex, findModel.directoryName
                                                                                                                          ?: FindBundle.message("find.scope.project.title")), cancellable = true) {
        FindInProjectUtil.findUsages(
          findModel,
          project,
          processor,
          FindUsagesProcessPresentation(UsageViewPresentation())
        )
      }
    } == null

    val entries = usages.mapNotNull { usage ->
      val file = usage.virtualFile ?: return@mapNotNull null
      val document = readAction { FileDocumentManager.getInstance().getDocument(file) } ?: return@mapNotNull null
      val textRange = usage.navigationRange ?: return@mapNotNull null
      val startLineNumber = document.getLineNumber(textRange.startOffset)
      val startLineStartOffset = document.getLineStartOffset(startLineNumber)
      val endLineNumber = document.getLineNumber(textRange.endOffset)
      val endLineEndOffset = document.getLineEndOffset(endLineNumber)
      val textBeforeOccurrence = document.getText(TextRange(startLineStartOffset, textRange.startOffset)).take(MAX_USAGE_TEXT_CHARS)
      val textInner = document.getText(TextRange(textRange.startOffset, textRange.endOffset)).take(MAX_USAGE_TEXT_CHARS)
      val textAfterOccurrence = document.getText(TextRange(textRange.endOffset, endLineEndOffset)).take(MAX_USAGE_TEXT_CHARS)
      UsageInfoEntry(projectDir.relativizeIfPossible(file), startLineNumber + 1, "$textBeforeOccurrence||$textInner||$textAfterOccurrence")
    }

    return UsageInfoResult(entries = entries, probablyHasMoreMatchingEntries = usages.size >= maxUsageCount, timedOut = timedOut)
  }

  @Serializable
  data class UsageInfoEntry(
    val filePath: String,
    val lineNumber: Int,
    val lineText: String,
  )

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  data class UsageInfoResult(
    val entries: List<UsageInfoEntry>,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val probablyHasMoreMatchingEntries: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val timedOut: Boolean = false
  )
}