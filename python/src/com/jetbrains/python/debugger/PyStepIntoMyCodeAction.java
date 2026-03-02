// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class PyStepIntoMyCodeAction extends XDebuggerActionBase
  implements TooltipDescriptionProvider, TooltipLinkProvider {

  private final XDebuggerSuspendedActionHandler myHandler = new XDebuggerSuspendedActionHandler() {
    @Override
    protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
      if (session.getDebugProcess() instanceof PyStepIntoSupport support) {
        support.performStepIntoMyCode();
      }
    }

    @Override
    protected boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
      return super.isEnabled(session, dataContext)
             && session.getDebugProcess() instanceof PyStepIntoSupport support
             && support.isStepIntoMyCodeAvailable();
    }
  };

  @Override
  @SuppressWarnings("deprecation")
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return myHandler;
  }

  @Override
  protected boolean isHidden(@NotNull AnActionEvent event) {
    return false;
  }

  @ApiStatus.Internal
  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    XDebugSession session = event.getData(XDebugSession.DATA_KEY);
    if (session == null) {
      Project project = event.getProject();
      if (project == null) return;
      session = XDebuggerManager.getInstance(project).getCurrentSession();
    }
    if (session == null) return;
    if (session.getDebugProcess() instanceof PyStepIntoSupport support) {
      String reason = support.getStepIntoMyCodeUnavailableReason();
      if (reason != null) {
        event.getPresentation().setDescription(reason);
      }
    }
  }

  @ApiStatus.Internal
  @Override
  public @Nullable TooltipLink getTooltipLink(@Nullable JComponent owner) {
    if (owner == null) return null;
    DataContext dataContext = DataManager.getInstance().getDataContext(owner);
    XDebugSession session = dataContext.getData(XDebugSession.DATA_KEY);
    if (session == null) {
      Project project = dataContext.getData(CommonDataKeys.PROJECT);
      if (project == null) return null;
      session = XDebuggerManager.getInstance(project).getCurrentSession();
    }
    if (session == null) return null;
    if (!(session.getDebugProcess() instanceof PyStepIntoSupport support)) return null;
    if (support.isStepIntoMyCodeAvailable()) return null;
    return new TooltipLink(PyBundle.message("debugger.step.into.my.code.switch.link"), () -> support.applyJustMyCodeChange(true));
  }
}
