// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

/**
 * Optional side-effect the panel must run after applying [PyInstallDialogVersionViewState].
 *
 * The presenter reports [None] when the last transition was a pure view refresh (nothing to
 * propagate outward), and [Fire] when the panel must call `onDescriptionToggled(value)` so the
 * hosting dialog syncs its own state (e.g. after auto-opening the description on selection).
 * Modelled as a sealed hierarchy rather than a nullable `Boolean` so the two states have names.
 */
internal sealed interface DescriptionToggleEvent {
  object None : DescriptionToggleEvent
  data class Fire(val descriptionVisible: Boolean) : DescriptionToggleEvent
}

/** View state for [PyInstallDialogVersionPanel]. */
internal data class PyInstallDialogVersionViewState(
  val packageInfoVisible: Boolean,
  val controlsVisible: Boolean,
  val installEnabled: Boolean,
  val descriptionVisible: Boolean,
  val descriptionToggleEvent: DescriptionToggleEvent,
)

/**
 * Pure logic presenter for [PyInstallDialogVersionPanel].
 *
 * Tracks the `isDescriptionVisible` flag and the `packageSelected` flag so the
 * panel's selection/clear/toggle transitions can be verified in plain JUnit5 tests
 * without any Swing or IntelliJ infrastructure.
 *
 * Sticky-toggle contract: [onSelectionCleared] hides the description (and fires
 * the callback) but does NOT reset the toggle widget's `isSelected` state — that
 * is the caller's responsibility. Consequently [onPackageSelected] re-opens the
 * description when `descriptionToggleSelected == true` and `!isDescriptionVisible`.
 */
internal class PyInstallDialogVersionPresenter {
  var isDescriptionVisible: Boolean = false
    private set
  private var packageSelected: Boolean = false

  fun onPackageSelected(descriptionToggleSelected: Boolean): PyInstallDialogVersionViewState {
    packageSelected = true
    val openNow = descriptionToggleSelected && !isDescriptionVisible
    if (openNow) isDescriptionVisible = true
    return buildState(if (openNow) DescriptionToggleEvent.Fire(true) else DescriptionToggleEvent.None)
  }

  fun onSelectionCleared(): PyInstallDialogVersionViewState {
    packageSelected = false
    val wasVisible = isDescriptionVisible
    if (wasVisible) isDescriptionVisible = false
    return buildState(if (wasVisible) DescriptionToggleEvent.Fire(false) else DescriptionToggleEvent.None)
  }

  fun onDescriptionToggled(): PyInstallDialogVersionViewState {
    isDescriptionVisible = !isDescriptionVisible
    return buildState(DescriptionToggleEvent.Fire(isDescriptionVisible))
  }

  private fun buildState(event: DescriptionToggleEvent): PyInstallDialogVersionViewState =
    PyInstallDialogVersionViewState(
      packageInfoVisible = packageSelected,
      controlsVisible = packageSelected,
      installEnabled = packageSelected,
      descriptionVisible = packageSelected && isDescriptionVisible,
      descriptionToggleEvent = event,
    )
}
