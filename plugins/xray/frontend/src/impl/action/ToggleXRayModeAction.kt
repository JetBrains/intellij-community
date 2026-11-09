// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.xray.frontend.api.XRayMode
import com.intellij.xray.frontend.impl.service.XRayModeService
import java.awt.event.KeyEvent

internal class ToggleXRayModeAction : DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val service = XRayModeService.getInstance()
    return service.isXRayModeEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return

    val service = XRayModeService.getInstance()

    val newState = if (state) XRayMode.BASIC else XRayMode.DISABLED
    service.setXRayMode(newState, editor)
    ActionToolbarImpl.updateAllToolbarsImmediately(false)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return

    val service = XRayModeService.getInstance()

    val fromHeldShortcut = (e.inputEvent as? KeyEvent)?.let { it.isControlDown && it.isShiftDown } == true
    if (!fromHeldShortcut) {
      service.cycleXRayMode(editor)
      ActionToolbarImpl.updateAllToolbarsImmediately(false)
      return
    }

    XRayPeekGesture.startIfNoneActive(editor)
  }
}
