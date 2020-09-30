package com.intellij.space.vcs.review.list

import com.intellij.openapi.ui.ComboBox
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class SpaceReviewListFiltersPanel(private val listVm: SpaceReviewsListVm) {

  private val searchTextField = object : SearchTextField() {
    override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
      if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
        listVm.spaceReviewsFilterSettings.value = listVm.spaceReviewsFilterSettings.value.copy(text = text)
        return true
      }
      return super.processKeyBinding(ks, e, condition, pressed)
    }
  }

  private val quickFiltersComboBox = ComboBox<ReviewListQuickFilter>(EnumComboBoxModel(ReviewListQuickFilter::class.java)).apply {
    addActionListener {
      val stateFilter = this.selectedItem as ReviewListQuickFilter
      listVm.spaceReviewsFilterSettings.value = listVm.quickFiltersMap.value[stateFilter] ?: error(
        "Unable to resolve quick filter settings for ${stateFilter}")
    }

    selectedItem = DEFAULT_QUICK_FILTER
  }

  val view = NonOpaquePanel(BorderLayout())

  init {
    val quickFiltersPanel = NonOpaquePanel(BorderLayout()).apply {
      add(JBLabel(SpaceBundle.message("label.quick.filters")), BorderLayout.WEST)
      add(quickFiltersComboBox, BorderLayout.CENTER)
    }

    view.add(searchTextField, BorderLayout.NORTH)
    view.add(quickFiltersPanel, BorderLayout.CENTER)
  }
}