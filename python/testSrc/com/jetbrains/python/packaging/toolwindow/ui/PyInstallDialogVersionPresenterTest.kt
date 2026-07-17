// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PyInstallDialogVersionPresenterTest {

  @Test
  fun `selecting package without toggle shows controls but not description`() {
    val presenter = PyInstallDialogVersionPresenter()
    val state = presenter.onPackageSelected(descriptionToggleSelected = false)
    assertTrue(state.packageInfoVisible)
    assertTrue(state.controlsVisible)
    assertTrue(state.installEnabled)
    assertFalse(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.None, state.descriptionToggleEvent)
  }

  @Test
  fun `selecting package with toggle open opens description`() {
    val presenter = PyInstallDialogVersionPresenter()
    val state = presenter.onPackageSelected(descriptionToggleSelected = true)
    assertTrue(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.Fire(true), state.descriptionToggleEvent)
  }

  @Test
  fun `selecting package with toggle when description already visible does not fire callback`() {
    val presenter = PyInstallDialogVersionPresenter()
    presenter.onPackageSelected(descriptionToggleSelected = true) // opens description
    val state = presenter.onPackageSelected(descriptionToggleSelected = true)
    assertTrue(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.None, state.descriptionToggleEvent)
  }

  @Test
  fun `clearing selection when description visible hides it and fires callback`() {
    val presenter = PyInstallDialogVersionPresenter()
    presenter.onPackageSelected(descriptionToggleSelected = true)
    val state = presenter.onSelectionCleared()
    assertFalse(state.packageInfoVisible)
    assertFalse(state.controlsVisible)
    assertFalse(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.Fire(false), state.descriptionToggleEvent)
  }

  @Test
  fun `clearing selection when description not visible fires no callback`() {
    val presenter = PyInstallDialogVersionPresenter()
    presenter.onPackageSelected(descriptionToggleSelected = false)
    val state = presenter.onSelectionCleared()
    assertFalse(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.None, state.descriptionToggleEvent)
  }

  @Test
  fun `sticky toggle re-opens description on next selection`() {
    // Simulates: user opens description, clears selection (toggle stays selected),
    // then selects another package — description should re-open automatically.
    val presenter = PyInstallDialogVersionPresenter()
    presenter.onPackageSelected(descriptionToggleSelected = true) // opens description
    presenter.onSelectionCleared()                                // hides description, toggle stays
    val state = presenter.onPackageSelected(descriptionToggleSelected = true)
    assertTrue(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.Fire(true), state.descriptionToggleEvent)
  }

  @Test
  fun `toggling description on fires callback with true`() {
    val presenter = PyInstallDialogVersionPresenter()
    presenter.onPackageSelected(descriptionToggleSelected = false)
    val state = presenter.onDescriptionToggled()
    assertTrue(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.Fire(true), state.descriptionToggleEvent)
  }

  @Test
  fun `toggling description off fires callback with false`() {
    val presenter = PyInstallDialogVersionPresenter()
    presenter.onPackageSelected(descriptionToggleSelected = true) // opens
    val state = presenter.onDescriptionToggled()
    assertFalse(state.descriptionVisible)
    assertEquals(DescriptionToggleEvent.Fire(false), state.descriptionToggleEvent)
  }

  @Test
  fun `clearing selection hides all controls`() {
    val presenter = PyInstallDialogVersionPresenter()
    presenter.onPackageSelected(descriptionToggleSelected = false)
    val state = presenter.onSelectionCleared()
    assertFalse(state.packageInfoVisible)
    assertFalse(state.controlsVisible)
    assertFalse(state.installEnabled)
  }
}
