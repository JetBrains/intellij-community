package circlet.reviews

import circlet.client.api.*
import circlet.platform.api.*
import circlet.ui.*
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.*
import com.intellij.util.ui.*
import java.awt.*
import javax.swing.*

class ReviewListItem(review: Review, preferredLanguage: TID?) : ComponentBasedList.Item {
    override val component: Component get() = panel

    private val panel = JPanel(GridLayoutManager(1, 4))
    private val id = JBLabel("#${review.id}")
    private val title = JBLabel(review.title)
    private val timestamp = JBLabel(review.timestamp.toString())
    private val createdBy = JBLabel(review.createdBy.fullname(preferredLanguage))

    init {
        panel.border = JBUI.Borders.empty(4)
        panel.background = UIUtil.getListBackground()

        val foreground = UIUtil.getListForeground()

        id.foreground = foreground
        panel.add(
            id,
            createFixedSizeGridConstraints(0, JBUI.size(80, -1))
        )

        title.foreground = foreground
        title.toolTipText = title.text
        panel.add(
            title,
            GridConstraints().apply {
                row = 0
                column = 1
                vSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                fill = GridConstraints.FILL_HORIZONTAL
                anchor = GridConstraints.ANCHOR_WEST
            }
        )

        timestamp.foreground = foreground
        timestamp.toolTipText = timestamp.text // TODO
        panel.add(
            timestamp,
            createFixedSizeGridConstraints(2, JBUI.size(160, -1))
        )

        createdBy.foreground = foreground
        createdBy.toolTipText = createdBy.text
        panel.add(
            createdBy,
            createFixedSizeGridConstraints(3, JBUI.size(320, -1))
        )
    }
}

private fun createFixedSizeGridConstraints(column: Int, size: JBDimension) =
    GridConstraints(
        0, column,
        1, 1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
        size, size, size
    )
