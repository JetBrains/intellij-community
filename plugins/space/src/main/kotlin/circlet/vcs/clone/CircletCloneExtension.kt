package circlet.vcs.clone

import circlet.components.circletWorkspace
import circlet.settings.*
import circlet.ui.cleanupUrl
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import icons.SpaceIcons
import platform.common.ProductName
import javax.swing.Icon

class CircletCloneExtension : VcsCloneDialogExtension {
    override fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
        return CircletCloneComponent(project)
    }

    override fun getIcon(): Icon = SpaceIcons.Main

    override fun getName(): String = ProductName

    override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
        val server = cleanupUrl(CircletSettings.getInstance().serverSettings.server)
        val status = if (server.isBlank()) "No account" else server
        return listOf(VcsCloneDialogExtensionStatusLine.greyText(status))
    }

    override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
        return CircletCloneComponent(project)
    }
}
