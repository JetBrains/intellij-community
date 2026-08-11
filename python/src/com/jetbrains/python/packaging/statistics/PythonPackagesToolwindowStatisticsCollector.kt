// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.statistics

import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object PythonPackagesToolwindowStatisticsCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  // Tool-window open/close and anchor changes are already covered by the platform "toolwindow" group
  // (ToolWindowEventLogGroup): `shown`/`activated`/`hidden` events with `id="Python Packages"` and
  // an `Anchor` `Location` field. This collector only tracks PPTW-specific interactions the
  // platform does not see.
  private val GROUP = EventLogGroup("python.packages.toolwindow", 2)

  internal val installPackageEvent: EventId = GROUP.registerEvent("installed")
  internal val uninstallPackageEvent: EventId = GROUP.registerEvent("uninstalled")
  internal val requestDetailsEvent: EventId = GROUP.registerEvent("details.requested")
  internal val repositoriesChangedEvent: EventId = GROUP.registerEvent("repositories.changed")

  // Install-dialog entry points: keyboard shortcut / PPTW header install button / gutter or context
  // action on an InstallablePackage tree node / dependency-group inlay hint.
  internal val installDialogOpenedEvent: EventId1<PyInstallDialogSource> = GROUP.registerEvent(
    "install.dialog.opened",
    EventFields.Enum<PyInstallDialogSource>("source"),
  )

  // Not covered by the platform "toolwindow" group: we need to attribute anchor changes
  // specifically to the PPTW toggle action (right/bottom button) with from/to, so users of the
  // dashboard can compare "user dragged" vs "user clicked our action" flows.
  internal val anchorToggledEvent: EventId2<ToolWindowMoveAction.Anchor, ToolWindowMoveAction.Anchor> = GROUP.registerEvent(
    "anchor.toggled",
    EventFields.Enum<ToolWindowMoveAction.Anchor>("from"),
    EventFields.Enum<ToolWindowMoveAction.Anchor>("to"),
  )

  // DialogMode is internal; keep the collector free of that dependency by using a String allow-list
  // that mirrors the DialogMode enum names.
  internal val installDialogInstallEvent: EventId2<String?, Boolean> = GROUP.registerEvent(
    "install.dialog.installed",
    EventFields.String("mode", listOf("SEARCH", "DIRECT_INSTALL", "COMMAND")),
    EventFields.Boolean("editable"),
  )

  // Editor-banner navigation from a dependency file to the PPTW.
  internal val navigateFromDependencyFileEvent: EventId = GROUP.registerEvent("navigate.from.dependency.file")
}

/**
 * Entry point that opened the [com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog].
 * Kept top-level (not nested in [PythonPackagesToolwindowStatisticsCollector]) so its FQN stays
 * short in the generated FUS events scheme.
 */
internal enum class PyInstallDialogSource {
  /** Keyboard shortcut / Find Action invocation of `PyInstallPackageAction`. */
  SHORTCUT,
  /** Install button in the PPTW header search bar. */
  HEADER,
  /** Click on the install icon of an `InstallablePackage` node in the tree. */
  LIST_ICON,
  /** Context-menu `InstallPackageAction` on a selected `InstallablePackage`. */
  INSTALLABLE_ACTION,
  /** `+ Add package` inlay hint inside a `pyproject.toml` dependency group. */
  INLAY_HINT,
}
