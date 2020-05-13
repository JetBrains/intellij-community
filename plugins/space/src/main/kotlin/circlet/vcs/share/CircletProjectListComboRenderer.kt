package circlet.vcs.share

import circlet.client.api.*
import com.intellij.ui.*
import javax.swing.*

class CircletProjectListComboRenderer : ColoredListCellRenderer<PR_Project>() {
    override fun customizeCellRenderer(list: JList<out PR_Project>,
                                       project: PR_Project?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
        project ?: return
        append(project.name)
        append("  ")
        append("key: ${project.key.key}", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
    }
}
