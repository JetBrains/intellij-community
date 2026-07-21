// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.ui

import com.jetbrains.python.packaging.toolwindow.ui.DialogMode
import com.jetbrains.python.packaging.toolwindow.ui.DialogModeContext
import com.jetbrains.python.packaging.toolwindow.ui.DialogModeStrategy
import com.jetbrains.python.packaging.toolwindow.ui.pickModeStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PyInstallDialogModeStrategyTest {

  private fun ctx(hasSelection: Boolean = false, commandHasArgs: Boolean = false) =
    DialogModeContext(hasSelection = hasSelection, commandHasArgs = commandHasArgs)

  @Test
  fun `blank query yields Search`() {
    assertTrue(pickModeStrategy("", isCliCommand = false) is DialogModeStrategy.Search)
    assertTrue(pickModeStrategy("   \t  ", isCliCommand = false) is DialogModeStrategy.Search)
  }

  @Test
  fun `CLI flag beats URL and path heuristics everything is treated as command args`() {
    // The dialog's `isCommand` guard already checked the SDK's cli-spec registry AND the
    // "command mode enabled" registry flag; when it returns true, the whole line is a command.
    val cliQuery = "pip install https://example.com/pkg.whl"
    val strategy = pickModeStrategy(cliQuery, isCliCommand = true)
    assertTrue(strategy is DialogModeStrategy.Command)
  }

  @Test
  fun `URL shapes route to DirectInstall with isUrl=true and the trimmed text`() {
    val strategy = pickModeStrategy("  https://example.com/pkg.tar.gz  ", isCliCommand = false)
    val direct = strategy as DialogModeStrategy.DirectInstall
    assertTrue(direct.isUrl)
    assertEquals("https://example.com/pkg.tar.gz", direct.text)
  }

  @Test
  fun `dot-relative paths route to DirectInstall with isUrl=false`() {
    val strategy = pickModeStrategy("./local/pkg", isCliCommand = false)
    val direct = strategy as DialogModeStrategy.DirectInstall
    assertFalse(direct.isUrl)
    assertEquals("./local/pkg", direct.text)
  }

  @Test
  fun `bare package names fall through to Search`() {
    // Names like `requests` look neither like a URL nor like a path, so the classifier ships
    // them to search mode — the search field then queries package repositories.
    assertTrue(pickModeStrategy("requests", isCliCommand = false) is DialogModeStrategy.Search)
    assertTrue(pickModeStrategy("django-cms", isCliCommand = false) is DialogModeStrategy.Search)
  }

  @Test
  fun `blank query wins over the CLI flag`() {
    // The blank-query branch runs before the CLI branch on purpose — an empty command line is
    // not a runnable command, it's the "no active search" state that Search handles.
    assertTrue(pickModeStrategy("", isCliCommand = true) is DialogModeStrategy.Search)
    assertTrue(pickModeStrategy("   ", isCliCommand = true) is DialogModeStrategy.Search)
  }

  @Test
  fun `file scheme URL routes to DirectInstall isUrl=true`() {
    val strategy = pickModeStrategy("file:///tmp/pkg.tar.gz", isCliCommand = false) as DialogModeStrategy.DirectInstall
    assertTrue(strategy.isUrl)
    assertEquals("file:///tmp/pkg.tar.gz", strategy.text)
  }

  @Test
  fun `git plus https URL routes to DirectInstall isUrl=true`() {
    // pip-style VCS URLs are still URL-shaped for the classifier — the `git+` prefix is a scheme.
    val strategy = pickModeStrategy("git+https://github.com/psf/requests", isCliCommand = false) as DialogModeStrategy.DirectInstall
    assertTrue(strategy.isUrl)
  }

  @Test
  fun `absolute unix path routes to DirectInstall isUrl=false`() {
    val strategy = pickModeStrategy("/tmp/pkg.tar.gz", isCliCommand = false) as DialogModeStrategy.DirectInstall
    assertFalse(strategy.isUrl)
    assertEquals("/tmp/pkg.tar.gz", strategy.text)
  }

  @Test
  fun `home-relative path routes to DirectInstall isUrl=false`() {
    val strategy = pickModeStrategy("~/pkg", isCliCommand = false) as DialogModeStrategy.DirectInstall
    assertFalse(strategy.isUrl)
    assertEquals("~/pkg", strategy.text)
  }

  @Test
  fun `single dot alone counts as a local path`() {
    // Anything starting with `.` is classified as a path; the ambiguous "just a dot" case still
    // routes to DirectInstall so the dialog surfaces an actionable error instead of a search.
    val strategy = pickModeStrategy(".", isCliCommand = false) as DialogModeStrategy.DirectInstall
    assertFalse(strategy.isUrl)
  }

  // ---------- Search.computeView ----------

  @Test
  fun `Search without selection hides everything except the results list`() {
    val view = DialogModeStrategy.Search.computeView(ctx(hasSelection = false))
    assertTrue(view.listScrollPaneVisible)
    assertNull(view.packageInfoText)
    assertFalse(view.editableVisible)
    assertFalse(view.versionButtonVisible)
    assertFalse(view.installButtonVisible)
    assertFalse(view.installButtonEnabled)
    assertFalse(view.bottomContainerVisible)
  }

  @Test
  fun `Search with a selection reveals the version button and install button`() {
    val view = DialogModeStrategy.Search.computeView(ctx(hasSelection = true))
    assertTrue(view.versionButtonVisible)
    assertTrue(view.installButtonVisible)
    assertTrue(view.installButtonEnabled)
    assertTrue(view.bottomContainerVisible)
  }

  @Test
  fun `Search collapses to short only when the user hasn't picked a package yet`() {
    assertTrue(DialogModeStrategy.Search.collapseToShort(ctx(hasSelection = false)))
    assertFalse(DialogModeStrategy.Search.collapseToShort(ctx(hasSelection = true)))
  }

  // ---------- DirectInstall.computeView ----------

  @Test
  fun `DirectInstall URL variant renders the URL info label with the link icon`() {
    val strategy = DialogModeStrategy.DirectInstall("https://example.com/pkg.whl", isUrl = true)
    val view = strategy.computeView(ctx())
    assertFalse(view.listScrollPaneVisible)
    assertTrue(view.editableVisible)
    assertTrue(view.installButtonEnabled)
    assertTrue(view.installButtonVisible)
    assertTrue(view.packageInfoText!!.contains("example.com"))
  }

  @Test
  fun `DirectInstall path variant renders the path info label with the folder icon`() {
    val strategy = DialogModeStrategy.DirectInstall("./local/pkg", isUrl = false)
    val view = strategy.computeView(ctx())
    assertTrue(view.packageInfoText!!.contains("./local/pkg"))
  }

  @Test
  fun `DirectInstall always collapses to short no dependency on selection state`() {
    val strategy = DialogModeStrategy.DirectInstall("./x", isUrl = false)
    assertTrue(strategy.collapseToShort(ctx(hasSelection = true)))
    assertTrue(strategy.collapseToShort(ctx(hasSelection = false)))
  }

  @Test
  fun `DirectInstall exposes its text as the dialog's directInstallText`() {
    val strategy = DialogModeStrategy.DirectInstall("/tmp/pkg.tar.gz", isUrl = false)
    assertEquals("/tmp/pkg.tar.gz", strategy.directInstallText)
  }

  // ---------- Command.computeView ----------

  @Test
  fun `Command mode disables the run button until the user typed at least one argument`() {
    // `pip` alone is just the tool alias — the runner needs at least one whitespace-separated
    // argument to actually invoke anything meaningful.
    val disabled = DialogModeStrategy.Command.computeView(ctx(commandHasArgs = false))
    assertFalse(disabled.installButtonEnabled)

    val enabled = DialogModeStrategy.Command.computeView(ctx(commandHasArgs = true))
    assertTrue(enabled.installButtonEnabled)
  }

  @Test
  fun `Command mode hides the results list and shows only the command info label`() {
    val view = DialogModeStrategy.Command.computeView(ctx(commandHasArgs = true))
    assertFalse(view.listScrollPaneVisible)
    assertFalse(view.editableVisible)
    assertFalse(view.versionButtonVisible)
    assertTrue(view.installButtonVisible)
    assertTrue(view.bottomContainerVisible)
  }

  // ---------- mode discriminator ----------

  @Test
  fun `strategy mode discriminator matches the enum variant`() {
    assertEquals(DialogMode.SEARCH, DialogModeStrategy.Search.mode)
    assertEquals(DialogMode.COMMAND, DialogModeStrategy.Command.mode)
    assertEquals(
      DialogMode.DIRECT_INSTALL,
      DialogModeStrategy.DirectInstall("x", isUrl = false).mode,
    )
  }
}
