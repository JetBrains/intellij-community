package circlet.vcs.clone

import circlet.client.api.*
import com.intellij.icons.*
import com.intellij.ui.*
import com.intellij.ui.components.panels.*
import com.intellij.util.ui.*
import libraries.io.*
import java.awt.*
import javax.swing.*

internal class CircletCloneListItemRenderer : ListCellRenderer<CircletCloneListItem> {
    private val starIcon: Icon = AllIcons.Nodes.Favorite as Icon

    private val insetsLeft: JBInsets = JBUI.insetsLeft(30)
    private val smallLeftInset: JBInsets = JBUI.insetsLeft(3)
    private val emptyTopBottomBorder: JBEmptyBorder = JBUI.Borders.empty(3, 0)

    private val projectNameComponent: SimpleColoredComponent = SimpleColoredComponent()
    private val projectDescriptionComponent: SimpleColoredComponent = SimpleColoredComponent()
    private val starLabel: SimpleColoredComponent = SimpleColoredComponent()
    private val projectIconLabel: SimpleColoredComponent = SimpleColoredComponent()

    private val projectRenderer: JPanel = JPanel(GridBagLayout()).apply {
        var gbc = GridBag().setDefaultAnchor(GridBag.WEST)

        gbc = gbc.nextLine().next()
            .weightx(0.0).fillCellVertically().coverColumn()
        add(projectIconLabel, gbc)

        gbc = gbc.next().weightx(0.0).weighty(0.5).fillCellHorizontally()
        add(projectNameComponent, gbc)

        gbc = gbc.next()
            .weightx(1.0).weighty(0.5).insets(smallLeftInset)
        add(starLabel, gbc)

        gbc = gbc.nextLine().next().next()
            .weightx(1.0).weighty(0.5).fillCellHorizontally().coverLine(2)
        add(projectDescriptionComponent, gbc)
        border = emptyTopBottomBorder
    }

    private val repoNameComponent: SimpleColoredComponent = SimpleColoredComponent().apply {
        ipad = insetsLeft
    }
    private val repoDetailsComponent: SimpleColoredComponent = SimpleColoredComponent().apply {
        ipad = insetsLeft
    }

    private val repoRenderer: JPanel = JPanel(VerticalLayout(0)).apply {
        add(repoNameComponent)
        add(repoDetailsComponent)
        border = emptyTopBottomBorder
    }

    override fun getListCellRendererComponent(list: JList<out CircletCloneListItem>,
                                              value: CircletCloneListItem,
                                              index: Int,
                                              selected: Boolean,
                                              focused: Boolean): JComponent {
        repoNameComponent.clear()
        repoNameComponent.append(value.repoInfo.name)

        repoDetailsComponent.clear()
        val details = value.repoDetails.value?.let { buildDetailsString(it, value.repoInfo) }
        if (details != null) {
            repoDetailsComponent.append(details, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
        else {
            repoDetailsComponent.append("Loading...", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }

        val model = list.model
        val withProject = index == 0 || model.getElementAt(index).project != model.getElementAt(index - 1).project
        if (!withProject) {
            UIUtil.setBackgroundRecursively(repoRenderer, ListUiUtil.WithTallRow.background(list, selected, true))
            return repoRenderer
        }

        projectNameComponent.clear()
        projectNameComponent.append(value.project.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        starLabel.icon = if (value.starred) starIcon else EmptyIcon.ICON_13

        val description: String =
            if (value.project.description.isNullOrBlank()) "Project key: ${value.project.key.key}"
            else value.project.description as String
        projectDescriptionComponent.clear()
        projectDescriptionComponent.append(description, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        return JPanel(VerticalLayout(0)).apply {
            add(projectRenderer)
            add(repoRenderer)
            UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected = false, hasFocus = true))
            UIUtil.setBackgroundRecursively(repoRenderer, ListUiUtil.WithTallRow.background(list, selected, true))
        }
    }

    private fun buildDetailsString(repoDetails: RepoDetails, repo: PR_RepositoryInfo) = with(repoDetails) {
        "${FileUtil.formatFileSize(size.size, withWhitespace = true)}   $totalBranches branches   $totalCommits commits   ${repo.description}"
    }
}
