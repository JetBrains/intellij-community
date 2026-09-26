// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

/**
 * Decides what happens when the SDK combo receives a new selection. The pane injects the three
 * side effects — hide the combo popup, navigate to the "All Interpreters" settings page, propagate
 * the value to the underlying `CollectionComboBoxModel` — so the presenter itself is Swing-free
 * and its dispatch is pinned by [SdkComboPresenterTest].
 */
internal class SdkComboPresenter(
  private val hidePopup: () -> Unit,
  private val navToAllInterpreters: () -> Unit,
  private val propagate: (SdkComboItem?) -> Unit,
) {

  /** Routes [item] to the injected side effect that matches its variant. */
  fun onSelectionChanged(item: SdkComboItem?): Unit = when (item) {
    SdkComboItem.ShowAll -> {
      hidePopup()
      navToAllInterpreters()
    }
    is SdkComboItem.Existing,
    SdkComboItem.NoInterpreter,
    null,
      -> propagate(item)
  }
}
