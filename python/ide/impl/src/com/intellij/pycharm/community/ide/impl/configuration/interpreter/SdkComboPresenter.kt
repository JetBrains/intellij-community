// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

/**
 * Decides what happens when the SDK combo receives a new selection. The pane injects the side effects: hide the combo
 * popup, navigate to the "All Interpreters" settings page, show or hide the interpreters of the other projects, and propagate
 * the value to the underlying `CollectionComboBoxModel`. So the presenter itself is Swing-free, and
 * [SdkComboPresenterTest] pins its dispatch.
 */
internal class SdkComboPresenter(
  private val hidePopup: () -> Unit,
  private val navToAllInterpreters: () -> Unit,
  private val toggleOtherProjects: () -> Unit,
  private val propagate: (SdkComboItem?) -> Unit,
) {

  /** Routes [item] to the injected side effect that matches its variant. */
  fun onSelectionChanged(item: SdkComboItem?): Unit = when (item) {
    SdkComboItem.ShowAll -> {
      hidePopup()
      navToAllInterpreters()
    }
    SdkComboItem.ToggleOtherProjects -> {
      hidePopup()
      toggleOtherProjects()
    }
    // The popup does not select a caption, so this branch only keeps the selection as it is.
    is SdkComboItem.GroupHeader -> Unit
    is SdkComboItem.Existing,
    SdkComboItem.NoInterpreter,
    null,
      -> propagate(item)
  }
}
