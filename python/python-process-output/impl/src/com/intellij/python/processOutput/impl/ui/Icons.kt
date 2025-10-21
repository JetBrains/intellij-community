package com.intellij.python.processOutput.impl.ui

import com.intellij.python.processOutput.impl.icons.PythonProcessOutputImplIcons
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal object Icons {
    @JvmField val CommandQueue = PythonProcessOutputImplIcons.CommandQueue

    object Keys {
        val Process = PathIconKey("/icons/process.svg", Icons::class.java)
        val ProcessBack = PathIconKey("/icons/processBack.svg", Icons::class.java)
        val ProcessBackError = PathIconKey("/icons/processBackError.svg", Icons::class.java)
        val ProcessError = PathIconKey("/icons/processError.svg", Icons::class.java)
        val Filter = AllIconsKeys.Actions.Show
        val Dropdown = AllIconsKeys.General.Dropdown
        val ExpandAll = AllIconsKeys.Actions.Expandall
        val CollapseAll = AllIconsKeys.Actions.Collapseall
        val Checked = AllIconsKeys.Actions.Checked
        val Search = AllIconsKeys.Actions.Search
        val Close = AllIconsKeys.Actions.Close
        val CloseHovered = AllIconsKeys.Actions.CloseHovered
        val Error = AllIconsKeys.General.Error
        val ChevronDown = AllIconsKeys.General.ChevronDown
        val ChevronRight = AllIconsKeys.General.ChevronRight
        val Copy = AllIconsKeys.General.Copy
        val Folder = AllIconsKeys.Nodes.Folder
    }
}
