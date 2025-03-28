package org.jetbrains.mcpserverplugin

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.mcpserverplugin.general.CreateNewFileWithTextTool
import org.jetbrains.mcpserverplugin.general.ExecuteActionByIdTool
import org.jetbrains.mcpserverplugin.general.FindFilesByNameSubstring
import org.jetbrains.mcpserverplugin.general.GetAllOpenFilePathsTool
import org.jetbrains.mcpserverplugin.general.GetAllOpenFileTextsTool
import org.jetbrains.mcpserverplugin.general.GetCurrentFilePathTool
import org.jetbrains.mcpserverplugin.general.GetCurrentFileTextTool
import org.jetbrains.mcpserverplugin.general.GetFileTextByPathTool
import org.jetbrains.mcpserverplugin.general.GetProgressIndicatorsTool
import org.jetbrains.mcpserverplugin.general.GetProjectDependenciesTool
import org.jetbrains.mcpserverplugin.general.GetProjectModulesTool
import org.jetbrains.mcpserverplugin.general.GetRunConfigurationsTool
import org.jetbrains.mcpserverplugin.general.GetSelectedTextTool
import org.jetbrains.mcpserverplugin.general.ListAvailableActionsTool
import org.jetbrains.mcpserverplugin.general.ListDirectoryTreeInFolderTool
import org.jetbrains.mcpserverplugin.general.ListFilesInFolderTool
import org.jetbrains.mcpserverplugin.general.OpenFileInEditorTool
import org.jetbrains.mcpserverplugin.general.ReplaceCurrentFileTextTool
import org.jetbrains.mcpserverplugin.general.ReplaceSelectedTextTool
import org.jetbrains.mcpserverplugin.general.ReplaceTextByPathTool
import org.jetbrains.mcpserverplugin.general.RunConfigurationTool
import org.jetbrains.mcpserverplugin.general.SearchInFilesContentTool
import org.jetbrains.mcpserverplugin.general.WaitTool
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
        )
    }
}