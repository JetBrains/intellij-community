package circlet.ui.clone

import circlet.client.api.*
import com.intellij.icons.*
import com.intellij.ui.*
import com.intellij.ui.components.panels.*
import com.intellij.util.ui.*
import libraries.io.*
import javax.swing.*

internal class CircletCloneListItemRenderer : ListCellRenderer<CircletCloneListItem> {
    private val starIcon: Icon = AllIcons.Nodes.Favorite as Icon

    private val insetsLeft: JBInsets = JBUI.insetsLeft(30)
    private val smallLeftInset: JBInsets = JBUI.insetsLeft(5)
    private val emptyTopBottomBorder: JBEmptyBorder = JBUI.Borders.empty(3, 0)

    private val projectNameComponent: SimpleColoredComponent = SimpleColoredComponent().apply {
        ipad = smallLeftInset
    }

    private val projectDescriptionComponent: SimpleColoredComponent = SimpleColoredComponent().apply {
        ipad = smallLeftInset
    }

    private val onlyProjectNameComponent: SimpleColoredComponent = SimpleColoredComponent().apply {
        ipad = smallLeftInset
        border = emptyTopBottomBorder
    }

    private val onlyProjectRenderer: Wrapper = Wrapper(onlyProjectNameComponent)

    private val projectRenderer: JPanel = JPanel(VerticalLayout(0)).apply {
        add(projectNameComponent)
        add(projectDescriptionComponent)
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

    private val detailsStringCache: MutableMap<RepoDetails, String> = mutableMapOf()

    override fun getListCellRendererComponent(list: JList<out CircletCloneListItem>,
                                              value: CircletCloneListItem,
                                              index: Int,
                                              selected: Boolean,
                                              focused: Boolean): JComponent {


        repoNameComponent.clear()
        repoNameComponent.append(value.repoInfo.name)

        repoDetailsComponent.clear()
        val details = detailsStringCache.getOrPut(value.repoDetails) { buildDetailsString(value.repoDetails, value.repoInfo) }
        repoDetailsComponent.append(details, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)

        val model = list.model
        val withProject = index == 0 || model.getElementAt(index).project != model.getElementAt(index - 1).project
        if (!withProject) {
            UIUtil.setBackgroundRecursively(repoRenderer, ListUiUtil.WithTallRow.background(list, selected))
            return repoRenderer
        }

        val prRenderer: JPanel
        val description = value.project.description
        if (description.isNullOrBlank()) {
            onlyProjectNameComponent.clear()
            onlyProjectNameComponent.append(value.project.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            if (value.starred) onlyProjectNameComponent.icon = starIcon
            prRenderer = onlyProjectRenderer
        }
        else {
            projectNameComponent.clear()
            projectNameComponent.append(value.project.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            if (value.starred) projectNameComponent.icon = starIcon
            projectDescriptionComponent.clear()
            projectDescriptionComponent.append(description, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            prRenderer = projectRenderer
        }

        return JBUI.Panels.simplePanel()
            .addToTop(prRenderer)
            .addToCenter(repoRenderer)
            .apply { UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, selected)) }
    }

    private fun buildDetailsString(repoDetails: RepoDetails, repo: PR_RepositoryInfo) = with(repoDetails) {
        "${FileUtil.formatFileSize(size.size, withWhitespace = true)}   $totalBranches branches   $totalCommits commits   ${repo.description}"
    }
}
