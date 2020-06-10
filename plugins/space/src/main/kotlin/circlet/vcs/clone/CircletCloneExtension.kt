package circlet.vcs.clone

import circlet.components.circletWorkspace
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
        // todo: simplify host obtaining
        val server = circletWorkspace.workspace.value?.client?.server?.let { cleanupUrl(it) }
        return listOf(VcsCloneDialogExtensionStatusLine.greyText(server ?: "No account"))
    }

    override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
        return CircletCloneComponent(project)
    }
}
