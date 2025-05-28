// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.mcpserver.general.CreateNewFileWithTextTool
import com.intellij.mcpserver.general.ExecuteActionByIdTool
import com.intellij.mcpserver.general.FindFilesByNameSubstring
import com.intellij.mcpserver.general.GetAllOpenFilePathsTool
import com.intellij.mcpserver.general.GetAllOpenFileTextsTool
import com.intellij.mcpserver.general.GetCurrentFileErrorsTool
import com.intellij.mcpserver.general.GetCurrentFilePathTool
import com.intellij.mcpserver.general.GetCurrentFileTextTool
import com.intellij.mcpserver.general.GetFileTextByPathTool
import com.intellij.mcpserver.general.GetProblemsTools
import com.intellij.mcpserver.general.GetProgressIndicatorsTool
import com.intellij.mcpserver.general.GetProjectDependenciesTool
import com.intellij.mcpserver.general.GetProjectModulesTool
import com.intellij.mcpserver.general.GetRunConfigurationsTool
import com.intellij.mcpserver.general.GetSelectedTextTool
import com.intellij.mcpserver.general.ListAvailableActionsTool
import com.intellij.mcpserver.general.ListDirectoryTreeInFolderTool
import com.intellij.mcpserver.general.ListFilesInFolderTool
import com.intellij.mcpserver.general.OpenFileInEditorTool
import com.intellij.mcpserver.general.ReplaceCurrentFileTextTool
import com.intellij.mcpserver.general.ReplaceSelectedTextTool
import com.intellij.mcpserver.general.ReplaceSpecificTextTool
import com.intellij.mcpserver.general.ReplaceTextByPathTool
import com.intellij.mcpserver.general.RunConfigurationTool
import com.intellij.mcpserver.general.SearchInFilesContentTool
import com.intellij.mcpserver.general.WaitTool
import com.intellij.mcpserver.git.GetVcsStatusTool
import com.intellij.mcpserver.general.ReformatCurrentFileTool
import com.intellij.mcpserver.general.ReformatFileTool

class McpToolManager {
    companion object {
        private val EP_NAME = ExtensionPointName<AbstractMcpTool<*>>("com.intellij.mcpServer.mcpTool")

        fun getAllTools(): List<AbstractMcpTool<*>> {
            return buildList {
                // Add built-in tools
                addAll(getBuiltInTools())
                // Add extension-provided tools
                addAll(EP_NAME.extensionList)
            }
        }

        private fun getBuiltInTools(): List<AbstractMcpTool<*>> = listOf(
            GetCurrentFileTextTool(),
            GetCurrentFilePathTool(),
            GetSelectedTextTool(),
            ReplaceSelectedTextTool(),
            ReplaceCurrentFileTextTool(),
            CreateNewFileWithTextTool(),
            FindFilesByNameSubstring(),
            GetFileTextByPathTool(),
            GetVcsStatusTool(),
            ToggleBreakpointTool(),
            GetBreakpointsTool(),
            ReplaceTextByPathTool(),
            ReplaceSpecificTextTool(),
            ListFilesInFolderTool(),
            ListDirectoryTreeInFolderTool(),
            SearchInFilesContentTool(),
            RunConfigurationTool(),
            GetRunConfigurationsTool(),
            GetProjectModulesTool(),
            GetProjectDependenciesTool(),
            GetAllOpenFileTextsTool(),
            GetAllOpenFilePathsTool(),
            OpenFileInEditorTool(),
            ListAvailableActionsTool(),
            ExecuteActionByIdTool(),
            GetProgressIndicatorsTool(),
            WaitTool(),
            GetCurrentFileErrorsTool(),
            ReformatCurrentFileTool(),
            ReformatFileTool(),
            GetProblemsTools(),
        )
    }
}