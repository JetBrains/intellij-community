package circlet.ui.clone

import circlet.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ui.cloneDialog.*
import git4idea.commands.*
import icons.*
import javax.swing.*

class CircletCloneExtension : VcsCloneDialogExtension {
    override fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
        return CircletCloneComponent(project,
                                     ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener,
                                     Git.getInstance())
    }

    override fun getIcon(): Icon = CircletIcons.mainIcon

    override fun getName(): String = "Space"

    override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
        // todo: simplify host obtaining
        val server = circletWorkspace.workspace.value?.client?.server
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.removeSuffix("/")
        return listOf(VcsCloneDialogExtensionStatusLine.greyText(server ?: "No account"))
    }
}
