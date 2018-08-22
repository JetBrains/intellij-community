package circlet.actions

import circlet.client.api.*
import circlet.ui.*
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.ui.components.*

class CloneCircletRepositoryAction: AnAction(){


    override fun actionPerformed(e: AnActionEvent) {
        CloneCircletRepositoryDialog(e.project!!).show()
    }
}
