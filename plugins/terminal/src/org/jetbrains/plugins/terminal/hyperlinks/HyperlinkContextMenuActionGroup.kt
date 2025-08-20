// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.terminal.session.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.IS_ALTERNATE_BUFFER_DATA_KEY

internal class HyperlinkContextMenuActionGroup : ActionGroup(), ActionRemoteBehaviorSpecification.BackendOnly {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    if (e == null) return emptyArray()
    // This excessive logging is to debug various strange remdev issues: "Why are there no remove actions?"
    LOG.trace { "getChildren(): event=$e" }
    val sessionId = e.dataContext.getData(TerminalSessionId.KEY) ?: return emptyArray()
    LOG.trace { "getChildren(): sessionId=$sessionId" }
    val isAltBuf = e.dataContext.getData(IS_ALTERNATE_BUFFER_DATA_KEY) ?: return emptyArray()
    LOG.trace { "getChildren(): isAltBuf=$isAltBuf" }
    val hyperlinkId = e.dataContext.getData(TerminalHyperlinkId.KEY) ?: return emptyArray()
    LOG.trace { "getChildren(): hyperlinkId=$hyperlinkId" }
    val hyperlinkInfoService = BackendHyperlinkInfoService.getInstance()
    LOG.trace { "getChildren(): hyperlinkInfoService=$hyperlinkInfoService" }
    val hyperlink = hyperlinkInfoService.getHyperlinkInfo(sessionId, isAltBuf, hyperlinkId) ?: return emptyArray()
    LOG.trace { "getChildren(): hyperlink=$hyperlink" }
    val mouseEvent = hyperlink.fakeMouseEvent
    LOG.trace { "getChildren(): mouseEvent=$mouseEvent" }
    val hyperlinkActionGroup = (hyperlink.hyperlinkInfo as? HyperlinkWithPopupMenuInfo)?.getPopupMenuGroup(mouseEvent)
    LOG.trace { "getChildren(): hyperlinkActionGroup=$hyperlinkActionGroup" }
    val result = hyperlinkActionGroup?.getChildren(e)
    LOG.trace { "getChildren(): result=${result.contentToString()}" }
    return result ?: emptyArray()
  }
}

private val LOG = logger<HyperlinkContextMenuActionGroup>()
