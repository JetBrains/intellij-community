// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.icons.AllIcons
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Presenter-side representation of the install dialog's search field mode.
 *
 * The install dialog runs in one of three UX modes depending on what the user typed — plain
 * search, direct install of a URL/path, or a raw CLI command against the SDK's registered
 * tools. Each mode has its own bottom-bar layout (info label, editable checkbox, version
 * button, install button). Before this refactor the "which widget is visible for which mode"
 * logic lived inline in `PyInstallPackageDialog` (`switchToSearchMode` / `switchToCommandMode`
 * / `switchToDirectInstallMode`), which meant the branches were untested and easy to break
 * when a widget was added or removed.
 *
 * [DialogModeStrategy] is one class per mode; each computes a [DialogModeView] snapshot from a
 * [DialogModeContext] snapshot and nothing else — no Swing types beyond [Icon] (which is a
 * plain interface). The dialog then applies the snapshot to its widgets in a single place.
 * Unit tests can construct a strategy, call [computeView] and assert against the resulting
 * `DialogModeView` without touching AWT.
 */
internal sealed interface DialogModeStrategy {
  /** Discriminator kept for the dialog's own routing (e.g. `performInstall` action dispatch). */
  val mode: DialogMode

  /**
   * When the strategy switches in, should we replace the dialog's `directInstallText`?
   * Only [DirectInstall] carries a value; the other modes clear the previous text.
   */
  val directInstallText: String

  /** `true` when transitioning to this mode should collapse the popup back to [ViewType.SHORT]. */
  fun collapseToShort(context: DialogModeContext): Boolean

  fun computeView(context: DialogModeContext): DialogModeView

  object Search : DialogModeStrategy {
    override val mode: DialogMode get() = DialogMode.SEARCH
    override val directInstallText: String get() = ""

    // Only collapse when the user hasn't picked a package yet — with a selection we keep the FULL
    // layout so the version chooser stays visible.
    override fun collapseToShort(context: DialogModeContext): Boolean = !context.hasSelection

    override fun computeView(context: DialogModeContext): DialogModeView {
      val hasSelection = context.hasSelection
      return DialogModeView(
        listScrollPaneVisible = true,
        packageInfoText = null,
        packageInfoIcon = null,
        editableVisible = false,
        versionButtonVisible = hasSelection,
        installButtonText = PyBundle.message("python.packaging.install.dialog.install"),
        installButtonEnabled = hasSelection,
        installButtonVisible = hasSelection,
        bottomContainerVisible = hasSelection,
      )
    }
  }

  data class DirectInstall(val text: String, val isUrl: Boolean) : DialogModeStrategy {
    override val mode: DialogMode get() = DialogMode.DIRECT_INSTALL
    override val directInstallText: String get() = text

    override fun collapseToShort(context: DialogModeContext): Boolean = true

    override fun computeView(context: DialogModeContext): DialogModeView {
      val infoText = if (isUrl) PyBundle.message("python.packaging.install.dialog.install.from.url", text)
      else PyBundle.message("python.packaging.install.dialog.install.from.path", text)
      return DialogModeView(
        listScrollPaneVisible = false,
        packageInfoText = infoText,
        packageInfoIcon = if (isUrl) AllIcons.Ide.Link else AllIcons.Nodes.Folder,
        editableVisible = true,
        versionButtonVisible = false,
        installButtonText = PyBundle.message("python.packaging.install.dialog.install"),
        installButtonEnabled = true,
        installButtonVisible = true,
        bottomContainerVisible = true,
      )
    }
  }

  object Command : DialogModeStrategy {
    override val mode: DialogMode get() = DialogMode.COMMAND
    override val directInstallText: String get() = ""

    override fun collapseToShort(context: DialogModeContext): Boolean = true

    override fun computeView(context: DialogModeContext): DialogModeView = DialogModeView(
      listScrollPaneVisible = false,
      packageInfoText = PyBundle.message("python.packaging.install.dialog.command.hint"),
      packageInfoIcon = AllIcons.Debugger.Console,
      editableVisible = false,
      versionButtonVisible = false,
      installButtonText = PyBundle.message("python.packaging.install.dialog.run.command"),
      // Command must include at least one whitespace-separated argument for the CLI parser
      // (`pip install foo` — not the bare `pip` alias).
      installButtonEnabled = context.commandHasArgs,
      installButtonVisible = true,
      bottomContainerVisible = true,
    )
  }
}

/**
 * Kept as an enum (rather than dropping it in favour of strategy `is` checks) so external
 * consumers — chiefly `performInstall`'s action dispatch — can pattern-match on the mode
 * without depending on the strategy hierarchy shape.
 */
internal enum class DialogMode { SEARCH, DIRECT_INSTALL, COMMAND }

/**
 * Snapshot of dialog state a strategy needs to compute its view. Kept minimal on purpose —
 * every field here is something the strategy actually branches on, so tests can construct a
 * plausible context by hand without having to fake the whole dialog.
 */
internal data class DialogModeContext(
  val hasSelection: Boolean,
  val commandHasArgs: Boolean,
)

/**
 * Immutable view state a strategy computes; the dialog applies it to widgets in a single place.
 * `packageInfoText == null` means the info label is hidden (and its icon is ignored).
 */
internal data class DialogModeView(
  val listScrollPaneVisible: Boolean,
  val packageInfoText: @Nls String?,
  val packageInfoIcon: Icon?,
  val editableVisible: Boolean,
  val versionButtonVisible: Boolean,
  val installButtonText: @Nls String,
  val installButtonEnabled: Boolean,
  val installButtonVisible: Boolean,
  val bottomContainerVisible: Boolean,
)

/**
 * Classifies the user-typed [query] into the strategy that owns it.
 *
 * [isCliCommand] is injected rather than derived here so the presenter can stay Swing/SDK free
 * — the caller checks the CLI-spec registry and the "command mode enabled" flag, then hands us
 * a simple boolean.
 *
 * Ordering matters: a blank query means "no active search", so we go straight to search mode
 * (even when the previous mode was something else); CLI takes precedence over the URL/path
 * heuristics because a URL-shaped argument to `pip install <url>` should still be treated as
 * part of the command, not as a package location.
 */
internal fun pickModeStrategy(query: String, isCliCommand: Boolean): DialogModeStrategy {
  val trimmed = query.trim()
  if (trimmed.isEmpty()) return DialogModeStrategy.Search
  if (isCliCommand) return DialogModeStrategy.Command
  if (isPackageUrl(trimmed)) return DialogModeStrategy.DirectInstall(trimmed, isUrl = true)
  if (isLocalPath(trimmed)) return DialogModeStrategy.DirectInstall(trimmed, isUrl = false)
  return DialogModeStrategy.Search
}
