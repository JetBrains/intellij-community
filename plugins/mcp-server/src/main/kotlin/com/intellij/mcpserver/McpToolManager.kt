// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.general.*
import com.intellij.mcpserver.git.GetVcsStatusTool
import com.intellij.openapi.extensions.ExtensionPointName

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
            // todo delete tools impl
            //ListAvailableActionsTool(),
            //ExecuteActionByIdTool(),
            //GetProgressIndicatorsTool(),
            //WaitTool(),
            GetCurrentFileErrorsTool(),
            ReformatCurrentFileTool(),
            ReformatFileTool(),
            GetProblemsTools(),
        )
    }
}