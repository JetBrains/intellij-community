package com.intellij.space.vcs.review.list

import circlet.client.api.TD_MemberProfile
import circlet.client.api.englishFullName
import circlet.code.api.CodeReviewParticipantRole
import circlet.code.api.CodeReviewWithCount
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.utils.formatAbsolute
import com.intellij.space.utils.toLocalDateTime
import com.intellij.space.vcs.review.ReviewUiSpec.avatarSizeIntValue
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
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class SpaceReviewListCellRenderer(
  private val avatarProvider: SpaceAvatarProvider,
  private val openButtonViewModel: OpenReviewButtonViewModel
) : ListCellRenderer<CodeReviewWithCount>,
    JPanel(null) {

  private val titleLabel: JLabel = JLabel().apply {
    isOpaque = false
  }

  private val infoLabel: JLabel = JLabel().apply {
    font = JBUI.Fonts.smallFont()
    isOpaque = false
  }

  private val emptyAvatar = EmptyIcon.create(JBUI.scale(avatarSizeIntValue.get()))

  private val authorAvatar: JLabel = JLabel(emptyAvatar)
  private val commentsLabel: JLabel = JLabel(AllIcons.Ide.Notification.NoEvents) // TODO: add new icon for comments

  private val firstReviewLabel: JLabel = JLabel(emptyAvatar)
  private val secondReviewLabel: JLabel = JLabel(emptyAvatar)

  private val openCodeReviewButton = OpenReviewButton.createOpenReviewButton("")


  init {
    val zero = "0"
    val gap = "${JBUI.scale(10)}px"
    val gapTop = "${JBUI.scale(5)}px"

    layout = MigLayout(LC().gridGap(zero, zero)
                         .insets(zero, zero, zero, zero)
                         .fillX())

    add(authorAvatar, CC()
      .gapAfter(gap)
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

  override fun getListCellRendererComponent(list: JList<out CodeReviewWithCount>,
                                            value: CodeReviewWithCount,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    background = ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus())
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(list, isSelected)

    val (reviewRef, messagesCount, _, participantsRef) = value

    val reviewers = participantsRef.resolve().participants?.filter {
      it.role == CodeReviewParticipantRole.Reviewer
    }

    val authors = participantsRef.resolve().participants?.filter {
      it.role == CodeReviewParticipantRole.Author
    }

    val review = reviewRef.resolve()
    val title = review.title
    val author = review.createdBy!!.resolve()
    val key = review.key ?: ""
    val localDateTime = review.createdAt.toLocalDateTime()
    val info = "$key by ${author.englishFullName()} ${localDateTime.formatAbsolute()}"

    val fullToolTipText = StringBuilder().apply {
      append(title).append(BR)
      append(info).append(BR)

      authors?.let {
        append(BR)
        append("Authors: ").append(BR)
        authors.forEach {
          append(it.user.resolve().englishFullName()).append(BR)
        }
      }
      reviewers?.let {
        append(BR)
        append("Reviewers: ").append(BR)
        reviewers.forEach {
          append(it.user.resolve().englishFullName()).append(BR)
        }
      }
    }.toString().let { XmlStringUtil.wrapInHtml(it) }

    titleLabel.apply {
      text = title
      foreground = primaryTextColor
      toolTipText = fullToolTipText
    }

    infoLabel.apply {
      text = info
      foreground = secondaryTextColor
      toolTipText = fullToolTipText
    }

    configureMemberLabel(authorAvatar, author)

    commentsLabel.apply {
      text = messagesCount.toString()
    }

    firstReviewLabel.apply {
      icon = emptyAvatar
      configureMemberLabel(firstReviewLabel, reviewers?.firstOrNull()?.user?.resolve())
    }
    secondReviewLabel.apply {
      icon = emptyAvatar
      configureMemberLabel(secondReviewLabel, reviewers?.secondOrNull()?.user?.resolve())
    }

    openCodeReviewButton.apply {
      isVisible = index == openButtonViewModel.hoveredRowIndex
      isOpaque = openButtonViewModel.isButtonHovered
    }

    return this
  }


  private fun configureMemberLabel(label: JLabel, profile: TD_MemberProfile?) {
    label.toolTipText = ""
    label.icon = emptyAvatar
    profile?.let {
      label.icon = avatarProvider.getIcon(it)
      label.toolTipText = it.englishFullName()
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