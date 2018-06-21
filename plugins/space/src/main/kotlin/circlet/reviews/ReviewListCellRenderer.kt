package circlet.reviews

import circlet.client.api.*
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.*
import com.intellij.util.ui.*
import java.awt.*
import javax.swing.*

class ReviewListCellRenderer : ListCellRenderer<CodeReviewWithCount> {
    private val panel = JPanel(GridLayoutManager(1, 4))
    private val id = JBLabel()
    private val title = JBLabel()
    private val timestamp = JBLabel()
    private val createdBy = JBLabel()

    init {
        panel.border = JBUI.Borders.empty(4)

        panel.add(
            id,
            createFixedSizeGridConstraints(0, JBUI.size(80, -1))
        )

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

        panel.add(
            timestamp,
            createFixedSizeGridConstraints(2, JBUI.size(160, -1))
        )

        panel.add(
            createdBy,
            createFixedSizeGridConstraints(3, JBUI.size(320, -1))
        )
    }

    override fun getListCellRendererComponent(
        list: JList<out CodeReviewWithCount>,
        value: CodeReviewWithCount?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val review =  value!!.review

        id.text = "#${review.id}"
        title.text = review.title
        timestamp.text = review.timestamp.toString()
        createdBy.text = review.createdBy.id

        val background: Color
        val foreground: Color

        if (isSelected) {
            background = list.selectionBackground
            foreground = list.selectionForeground
        }
        else {
            background = list.background
            foreground = list.foreground
        }

        panel.background = background

        id.foreground = foreground
        title.foreground = foreground
        timestamp.foreground = foreground
        createdBy.foreground = foreground

        return panel
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
