// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.BackgroundRoundedPanel
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.border.Border

internal class PyCommunityToUnifiedPromoDialog(project: Project?) :
  DialogWrapper(project, null, false,
                IdeModalityType.IDE, true, false) {

  private val imageIcon = PyPromoSharedComponents.popUpImg

  override fun createContentPaneBorder(): Border {
    return JBUI.Borders.empty()
  }

  override fun getStyle(): DialogStyle {
    return super.getStyle()
  }

  init {
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)
    WindowRoundedCornersManager.configure(this)
    init()
  }

  override fun createSouthPanel(): JComponent? {
    return panel {
      row {
        button(PyPromoSharedComponents.updateNow) {
          close(OK_EXIT_CODE)
        }.focused()
          .applyToComponent {
            putClientProperty("gotItButton", true)
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            this@PyCommunityToUnifiedPromoDialog.rootPane.defaultButton = this
          }.customize(customGaps = UnscaledGaps(right = 10))
        link(PyPromoSharedComponents.skip) {
          close(CANCEL_EXIT_CODE)
        }
      }
    }.also {
      setSize(imageIcon.iconWidth, it.preferredSize.height)
      it.border = JBUI.Borders.empty(0, 28, 28, 28)
    }
  }


  override fun createCenterPanel(): JComponent {
    val mainPanel = BackgroundRoundedPanel(16, BorderLayout())
    val imagePanel = createImagePanel()
    val infoPanel = createInfoPanel()
    mainPanel.add(imagePanel, BorderLayout.NORTH)
    mainPanel.add(infoPanel, BorderLayout.SOUTH)
    mainPanel.preferredSize = Dimension(imageIcon.iconWidth, 200)
    pack()
    return mainPanel
  }

  private fun createImagePanel(): JPanel {
    val imagePanel = JPanel(BorderLayout())
    val imageLabel = JBLabel(imageIcon)
    imagePanel.add(imageLabel, BorderLayout.CENTER)
    return imagePanel
  }

  private fun createInfoPanel(): JPanel {
    return panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          label(PyPromoSharedComponents.headerTitle)
            .applyToComponent {
              font = JBFont.h2().asBold()
            }
        }
        row {
          text(PyPromoSharedComponents.mainText, maxLineLength = 40)
            .applyToComponent {
              foreground = PyPromoSharedComponents.infoFontColor
            }
        }.customize(customRowGaps = UnscaledGapsY(top = 4))
        row {
          browserLink(PyPromoSharedComponents.learnMore, PyPromoSharedComponents.LEARN_MORE_URL)
        }.customize(customRowGaps = UnscaledGapsY(top = 4, bottom = 12))
      }
    }.also {
      setSize(imageIcon.iconWidth, it.preferredSize.height)
      it.border = JBUI.Borders.empty(28, 28, 0, 28)
    }
  }
}