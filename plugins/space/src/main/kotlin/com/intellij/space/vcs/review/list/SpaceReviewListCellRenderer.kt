// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.list

import circlet.client.api.TD_MemberProfile
import circlet.client.api.englishFullName
import circlet.code.api.CodeReviewListItem
import circlet.code.api.CodeReviewParticipantRole
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.utils.formatPrettyDateTime
import com.intellij.space.vcs.review.ReviewUiSpec.avatarSizeIntValue
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.BR
import com.intellij.util.ui.codereview.OpenReviewButton
import com.intellij.util.ui.codereview.OpenReviewButtonViewModel
import com.intellij.xml.util.XmlStringUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer


internal class SpaceReviewListCellRenderer(
  private val avatarProvider: SpaceAvatarProvider,
  private val openButtonViewModel: OpenReviewButtonViewModel
) : ListCellRenderer<CodeReviewListItem>,
    JPanel(null) {

  private val commentIcon: Icon
    get() = AllIcons.Ide.Notification.NoEvents

  private val titleLabel: SimpleColoredComponent = SimpleColoredComponent().apply {
    isOpaque = false
  }

  private val infoLabel: SimpleColoredComponent = SimpleColoredComponent().apply {
    font = JBUI.Fonts.smallFont()
    isOpaque = false
  }

  private val emptyAvatar = EmptyIcon.create(JBUI.scale(avatarSizeIntValue.get()))

  private val authorAvatar: SimpleColoredComponent = SimpleColoredComponent().apply {
    icon = emptyAvatar
  }

  private val commentsLabel: SimpleColoredComponent = SimpleColoredComponent().apply {
    icon = commentIcon
  }

  private val firstReviewLabel: SimpleColoredComponent = SimpleColoredComponent().apply {
    icon = emptyAvatar
  }
  private val secondReviewLabel: SimpleColoredComponent = SimpleColoredComponent().apply {
    icon = emptyAvatar
  }

  private val openCodeReviewButton = OpenReviewButton.createOpenReviewButton()


  init {
    val zero = "0"
    val gap = "${JBUI.scale(8)}px"
    val gapTop = "${JBUI.scale(5)}px"

    layout = MigLayout(LC().gridGap(zero, zero)
                         .insets(zero, zero, zero, zero)
                         .fillX())

    add(authorAvatar, CC()
      .gapAfter(zero)
      .gapBefore(gap)
      .gapBottom(gap)
      .gapTop(gap)
      .center()
      .spanY(2)
      .shrinkPrioX(100)
    )
    add(titleLabel, CC()
      .gapTop(gapTop)
      .growX()
      .pushX()
      .minWidth("0px")
      .spanX(2)
      .shrinkPrioX(15)
      .gapAfter(gap)
    )
    add(commentsLabel, CC()
      .spanY(2)
      .center()
      .gapAfter(gap)
      .shrinkPrioX(100)
    )
    add(firstReviewLabel, CC()
      .spanY(2)
      .center()
      .gapAfter(gap)
      .shrinkPrioX(10)
    )
    add(secondReviewLabel, CC()
      .spanY(2)
      .center()
      .gapAfter(gap)
      .shrinkPrioX(9)
    )
    add(openCodeReviewButton, CC()
      .spanY(2)
      .center()
      .shrinkPrioX(1000)
      .growY()
      .minWidth("pref")
      .wrap()
    )
    add(infoLabel, CC()
      .gapBottom(gapTop)
      .skip(1)
      .gapAfter(gap)
      .growX()
      .pushX()
      .minWidth("0px")
    )
    this.components.forEach {
      UIUtil.setNotOpaqueRecursively(it)
    }
  }

  override fun getListCellRendererComponent(list: JList<out CodeReviewListItem>,
                                            value: CodeReviewListItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    background = ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus())
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(list, isSelected)

    val participants = value.participants.resolve().participants

    val reviewers = participants?.filter {
      it.role == CodeReviewParticipantRole.Reviewer
    }

    val authors = participants?.filter {
      it.role == CodeReviewParticipantRole.Author
    }

    val review = value.review.resolve()
    val title = review.title
    val author = review.createdBy?.resolve()
    val key = review.key ?: ""
    val createdAt = review.createdAt.formatPrettyDateTime()
    val info = author?.let { SpaceBundle.message("review.by.author.at.time", key, it.englishFullName(), createdAt) }
               ?: SpaceBundle.message("review.at.time", key, createdAt)

    val fullToolTipText = StringBuilder().apply {
      append(title).append(BR)
      append(info).append(BR)

      authors?.let {
        append(BR)
        append(SpaceBundle.message("review.details.authors.label")).append(BR)
        authors.forEach {
          append(it.user.resolve().englishFullName()).append(BR)
        }
      }
      reviewers?.let {
        append(BR)
        append(SpaceBundle.message("review.details.reviewers.label")).append(BR)
        reviewers.forEach {
          append(it.user.resolve().englishFullName()).append(BR)
        }
      }
    }.toString().let { XmlStringUtil.wrapInHtml(it) } // NON-NLS

    titleLabel.apply {
      clear()
      append(title)
      foreground = primaryTextColor
      toolTipText = fullToolTipText
    }

    infoLabel.apply {
      clear()
      append(info)
      foreground = secondaryTextColor
      toolTipText = fullToolTipText
    }

    configureMemberLabel(authorAvatar, author)

    commentsLabel.apply {
      clear()
      icon = commentIcon
      append(value.discussionCount.resolve().counter.total.toString())// NON-NLS
    }

    firstReviewLabel.apply {
      configureMemberLabel(firstReviewLabel, reviewers?.firstOrNull()?.user?.resolve())
    }
    secondReviewLabel.apply {
      configureMemberLabel(secondReviewLabel, reviewers?.secondOrNull()?.user?.resolve())
    }

    openCodeReviewButton.apply {
      isVisible = index == openButtonViewModel.hoveredRowIndex
      isOpaque = openButtonViewModel.isButtonHovered
    }

    return this
  }


  private fun configureMemberLabel(label: SimpleColoredComponent, profile: TD_MemberProfile?) {
    if (profile != null) {
      label.icon = avatarProvider.getIcon(profile)
      label.toolTipText = profile.englishFullName() // NON-NLS
    }
    else {
      label.icon = emptyAvatar
      label.toolTipText = ""
    }
  }
}

private fun <T> List<T>?.secondOrNull(): T?  {
  if (this?.size ?: 0 > 1) {
    return this!![1]
  }
  return null
}

private fun CC.center(): CC = this.alignX("center").alignY("center")