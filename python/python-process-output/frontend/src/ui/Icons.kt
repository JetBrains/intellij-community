package com.intellij.python.processOutput.frontend.ui

import com.intellij.python.processOutput.frontend.icons.PythonProcessOutputFrontendIcons
import javax.swing.Icon
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal object Icons {
    object Keys {
        val Process = PythonProcessOutputFrontendIcons.Process.toJewelKey()
        val ResultIncorrect = PythonProcessOutputFrontendIcons.ResultIncorrect.toJewelKey()
        val ProcessHeavy = PythonProcessOutputFrontendIcons.ProcessHeavy.toJewelKey()
        val ProcessMedium = PythonProcessOutputFrontendIcons.ProcessMedium.toJewelKey()
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

    private fun Icon.toJewelKey() =
        IntelliJIconKey.fromPlatformIcon(this, PythonProcessOutputFrontendIcons::class.java)
}
