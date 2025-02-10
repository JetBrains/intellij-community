package org.jetbrains.mcpserverplugin

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.mcpserverplugin.git.GetVcsStatusTool

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
            ListFilesInFolderTool(),
            SearchInFilesContentTool(),
            RunConfigurationTool(),
            GetRunConfigurationsTool(),
            GetProjectModulesTool(),
            GetProjectDependenciesTool(),
            GetAllOpenFileTextsTool(),
            GetAllOpenFilePathsTool(),
            OpenFileInEditorTool(),
        )
    }
}