@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.find.FindBundle
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.currentCoroutineContext

class TextToolset : McpToolset {
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
    val (document, rangeMarkers) = readAction {
      Cancellation.ensureActive()
      val rangeMarkers = mutableListOf<RangeMarker>()
      val document = FileDocumentManager.getInstance().getDocument(file) ?: mcpFail("Could not get document for $file")
      val text = document.text

      // Special handling for empty oldText
      if (oldText.isEmpty()) {
        if (text.isEmpty()) {
          // Allow setting newText on empty file (LLM create-then-fill workflow)
          val rangeMarker = document.createRangeMarker(0, 0, true)
          rangeMarkers.add(rangeMarker)
        } else {
          // Fail if file is not empty to prevent endless loop
          mcpFail("oldText is empty but file is not empty")
        }
      } else {
        // Normal case: search for oldText
        var currentStartIndex = 0
        while (true) {
          Cancellation.checkCancelled()
          val occurrenceStart = text.indexOf(oldText, currentStartIndex, !caseSensitive)
          if (occurrenceStart < 0) break
          val rangeMarker = document.createRangeMarker(occurrenceStart, occurrenceStart + oldText.length, true)
          rangeMarkers.add(rangeMarker)
          if (!replaceAll) break // only the first occurence
          currentStartIndex = occurrenceStart + oldText.length
        }
      }
      document to rangeMarkers.toList()
    }

    if (rangeMarkers.isEmpty()) mcpFail("No occurrences found")

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

}
