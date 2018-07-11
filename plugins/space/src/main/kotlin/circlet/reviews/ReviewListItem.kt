package circlet.reviews

import circlet.client.api.*
import circlet.platform.api.*
import circlet.ui.*
import circlet.utils.*
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.*
import com.intellij.util.ui.*
import javax.swing.*

class ReviewListItem(val review: Review, preferredLanguage: TID?) : JComponentBasedList.Item {
    override val component: JComponent get() = panel

    override var selectionState: JComponentBasedList.Item.SelectionState = JComponentBasedList.Item.SelectionState.Unselected
        set(value) {
            field = value

            updateSelection(value)
        }

    private val panel = JPanel(GridLayoutManager(1, 4))
    private val id = JBLabel("#${review.id}")
    private val title = JBLabelWithSizeCheckingToolTip(review.title)
    private val createdAt = JBLabel(review.createdAt.formatRelative())
    private val createdBy = JBLabelWithSizeCheckingToolTip(review.createdBy.fullname(preferredLanguage))

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

        createdAt.toolTipText = review.createdAt.formatAbsolute()
        panel.add(
            createdAt,
            createFixedSizeGridConstraints(2, JBUI.size(160, -1))
        )

        createdBy.toolTipText = createdBy.text
        panel.add(
            createdBy,
            createFixedSizeGridConstraints(3, JBUI.size(320, -1))
        )

        updateSelection()
    }

    private fun updateSelection() {
        updateSelection(selectionState)
    }

    private fun updateSelection(newSelectionState: JComponentBasedList.Item.SelectionState) {
        panel.background = newSelectionState.background

        val foreground = newSelectionState.foreground

        id.foreground = foreground
        title.foreground = foreground
        createdAt.foreground = foreground
        createdBy.foreground = foreground
    }

    override fun onLookAndFeelChanged() {
        updateSelection()
    }
}

private class JBLabelWithSizeCheckingToolTip(text: String) : JBLabel(text) {
    override fun getToolTipText(): String? = super.getToolTipText().takeIf { preferredSize.width > size.width }
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
