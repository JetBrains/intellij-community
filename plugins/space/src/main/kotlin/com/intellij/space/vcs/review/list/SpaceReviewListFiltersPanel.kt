// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.list

import com.intellij.openapi.ui.ComboBox
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class SpaceReviewListFiltersPanel(private val listVm: SpaceReviewsListVm) {

  private val searchTextField = object : SearchTextField("space.review.list.search.text") {

    override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
      if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
        onTextChanged()
        return true
      }
      return super.processKeyBinding(ks, e, condition, pressed)
    }

    override fun onFocusLost() {
      super.onFocusLost()
      onTextChanged()
    }

    override fun onFieldCleared() {
      onTextChanged()
    }

    private fun onTextChanged() {
      val newText = text.trim()
      listVm.textToSearch.value = newText
      SpaceStatsCounterCollector.CHANGE_TEXT_FILTER.log(newText.isBlank())
    }
  }

  private val quickFiltersComboBox = ComboBox(EnumComboBoxModel(ReviewListQuickFilter::class.java)).apply {
    addActionListener {
      val stateFilter = this.selectedItem as ReviewListQuickFilter
      val newFilter = listVm.quickFiltersMap.value[stateFilter]
      listVm.spaceReviewsQuickFilter.value = newFilter ?: error("Unable to resolve quick filter settings for ${stateFilter}")
      SpaceStatsCounterCollector.CHANGE_QUICK_FILTER.log(stateFilter)
    }

    selectedItem = DEFAULT_QUICK_FILTER
  }

  val view = NonOpaquePanel(BorderLayout())

  init {
    val quickFiltersPanel = NonOpaquePanel(BorderLayout()).apply {
      add(JBLabel(SpaceBundle.message("label.quick.filters")).withBorder(JBUI.Borders.empty(0, 5)), BorderLayout.WEST)
      add(quickFiltersComboBox, BorderLayout.CENTER)
    }

    view.add(searchTextField, BorderLayout.NORTH)
    view.add(quickFiltersPanel, BorderLayout.CENTER)
  }
}