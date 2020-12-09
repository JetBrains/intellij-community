// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.clone

import circlet.client.api.PR_RepositoryInfo
import circlet.client.api.RepoDetails
import com.intellij.icons.AllIcons
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.*
import libraries.io.FileUtil
import org.jetbrains.annotations.Nls
import java.awt.GridBagLayout
import javax.swing.*

internal class SpaceCloneListItemRenderer : ListCellRenderer<SpaceCloneListItem> {
  private val starIcon: Icon = AllIcons.Nodes.Favorite

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

  override fun getListCellRendererComponent(list: JList<out SpaceCloneListItem>,
                                            value: SpaceCloneListItem,
                                            index: Int,
                                            selected: Boolean,
                                            focused: Boolean): JComponent {
    repoNameComponent.clear()
    repoNameComponent.append(value.repoInfo.name) // NON-NLS

    repoDetailsComponent.clear()
    val details = value.repoDetails.value?.let { buildDetailsString(it, value.repoInfo) }
    if (details != null) {
      repoDetailsComponent.append(details, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    else {
      repoDetailsComponent.append(
        SpaceBundle.message("clone.dialog.projects.list.repository.description.loading"),
        SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
      )
    }

    val model = list.model
    val withProject = index == 0 || model.getElementAt(index).project != model.getElementAt(index - 1).project
    if (!withProject) {
      UIUtil.setBackgroundRecursively(repoRenderer, ListUiUtil.WithTallRow.background(list, selected, true))
      return repoRenderer
    }

    projectNameComponent.clear()

    val spaceProject = value.project
    projectNameComponent.append(spaceProject.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) // NON-NLS
    starLabel.icon = if (value.starred) starIcon else EmptyIcon.ICON_13

    val projectDescription = spaceProject.description
    val description = if (projectDescription.isNullOrBlank()) {
      SpaceBundle.message("clone.dialog.projects.list.project.description", spaceProject.key.key)
    }
    else {
      projectDescription
    }
    projectDescriptionComponent.clear()
    projectDescriptionComponent.append(description, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    return JPanel(VerticalLayout(0)).apply {
      add(projectRenderer)
      add(repoRenderer)
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected = false, hasFocus = true))
      UIUtil.setBackgroundRecursively(repoRenderer, ListUiUtil.WithTallRow.background(list, selected, true))
    }
  }

  @Nls
  private fun buildDetailsString(repoDetails: RepoDetails, repo: PR_RepositoryInfo) = with(repoDetails) {
    SpaceBundle.message(
      "clone.dialog.projects.list.repository.description",
      FileUtil.formatFileSize(size.size, withWhitespace = true),
      totalBranches,
      totalCommits,
      repo.description
    )
  }
}
