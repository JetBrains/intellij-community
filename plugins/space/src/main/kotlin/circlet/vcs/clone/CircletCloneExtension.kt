package circlet.vcs.clone

import circlet.components.*
import circlet.ui.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vcs.ui.cloneDialog.*
import icons.*
import platform.common.*
import javax.swing.*

class CircletCloneExtension : VcsCloneDialogExtension {
    override fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
        return CircletCloneComponent(project)
    }

    override fun getIcon(): Icon = CircletIcons.mainIcon

    override fun getName(): String = ProductName

    override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
        // todo: simplify host obtaining
        val server = circletWorkspace.workspace.value?.client?.server?.let { cleanupUrl(it) }
        return listOf(VcsCloneDialogExtensionStatusLine.greyText(server ?: "No account"))
    }
}
