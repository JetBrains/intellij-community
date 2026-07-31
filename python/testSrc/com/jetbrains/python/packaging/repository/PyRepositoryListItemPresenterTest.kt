// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PyRepositoryListItemPresenterTest {

  @Test
  fun `no errors for unique name and valid url`() {
    val presenter = presenter(isDefault = false, names = listOf("other-repo"))
    val state = presenter.computeViewState("my-repo", "https://pypi.org/simple/")
    assertFalse(state.nameError)
    assertFalse(state.urlError)
  }

  @Test
  fun `name error when name is duplicate`() {
    val presenter = presenter(isDefault = false, names = listOf("my-repo", "my-repo"))
    assertTrue(presenter.computeViewState("my-repo", "https://pypi.org/simple/").nameError)
  }

  @Test
  fun `no name error when name is blank`() {
    val presenter = presenter(isDefault = false, names = listOf("", ""))
    assertFalse(presenter.computeViewState("", "https://pypi.org/simple/").nameError)
  }

  @Test
  fun `url error for url with no scheme`() {
    val presenter = presenter(isDefault = false, names = emptyList())
    assertTrue(presenter.computeViewState("repo", "not-a-url").urlError)
  }

  @Test
  fun `url error for ftp scheme`() {
    val presenter = presenter(isDefault = false, names = emptyList())
    assertTrue(presenter.computeViewState("repo", "ftp://example.com/simple/").urlError)
  }

  @Test
  fun `no url error for blank url`() {
    // blank url is not checked — blank means "not yet typed", not "invalid"
    val presenter = presenter(isDefault = false, names = emptyList())
    assertFalse(presenter.computeViewState("repo", "").urlError)
  }

  @Test
  fun `no url error for valid https url`() {
    val presenter = presenter(isDefault = false, names = emptyList())
    assertFalse(presenter.computeViewState("repo", "https://example.com/simple/").urlError)
  }

  @Test
  fun `isDefault suppresses both name and url errors`() {
    val presenter = presenter(isDefault = true, names = listOf("dup", "dup"))
    val state = presenter.computeViewState("dup", "not-a-url")
    assertFalse(state.nameError)
    assertFalse(state.urlError)
  }

  @Test
  fun `hasErrors returns true when name is duplicate`() {
    val presenter = presenter(isDefault = false, names = listOf("dup", "dup"))
    assertTrue(presenter.hasErrors("dup", "https://example.com/simple/"))
  }

  @Test
  fun `hasErrors returns true when url is invalid`() {
    val presenter = presenter(isDefault = false, names = emptyList())
    assertTrue(presenter.hasErrors("repo", "bad-url"))
  }

  @Test
  fun `hasErrors returns false when default even if both fields invalid`() {
    val presenter = presenter(isDefault = true, names = listOf("dup", "dup"))
    assertFalse(presenter.hasErrors("dup", "bad-url"))
  }

  private fun presenter(isDefault: Boolean, names: List<String>): PyRepositoryListItemPresenter =
    PyRepositoryListItemPresenter(isDefault = isDefault, getAllNames = { names })
}
