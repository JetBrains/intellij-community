package circlet.reviews

import circlet.client.api.*
import circlet.platform.api.*
import circlet.ui.*
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.*
import com.intellij.util.ui.*
import javax.swing.*

class ReviewListItem(review: Review, preferredLanguage: TID?) : JComponentBasedList.Item {
    override val component: JComponent get() = panel

    override var selected: Boolean = false
        set(value) {
            field = value

            updateSelected(value)
        }

    private val panel = JPanel(GridLayoutManager(1, 4))
    private val id = JBLabel("#${review.id}")
    private val title = JBLabel(review.title)
    private val timestamp = JBLabel(review.timestamp.toString())
    private val createdBy = JBLabel(review.createdBy.fullname(preferredLanguage))

    init {
        panel.border = JBUI.Borders.empty(4)

        panel.add(
            id,
            createFixedSizeGridConstraints(0, JBUI.size(80, -1))
        )

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

        timestamp.toolTipText = timestamp.text // TODO
        panel.add(
            timestamp,
            createFixedSizeGridConstraints(2, JBUI.size(160, -1))
        )

        createdBy.toolTipText = createdBy.text
        panel.add(
            createdBy,
            createFixedSizeGridConstraints(3, JBUI.size(320, -1))
        )

        updateSelected(selected)
    }

    private fun updateSelected(newSelected: Boolean) {
        panel.background = UIUtil.getListBackground(newSelected)

        val foreground = UIUtil.getListForeground(newSelected)

        id.foreground = foreground
        title.foreground = foreground
        timestamp.foreground = foreground
        createdBy.foreground = foreground
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
