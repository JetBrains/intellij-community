package com.intellij.xdebugger.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.actions.handlers.*;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelProvider;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.XQuickEvaluateHandler;
import com.intellij.xdebugger.impl.settings.DebuggerSettingsPanelProvider;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsPanelProviderImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebuggerSupport extends DebuggerSupport {
  private XBreakpointPanelProvider myBreakpointPanelProvider;
  private XToggleLineBreakpointActionHandler myToggleLineBreakpointActionHandler;
  private XDebuggerSuspendedActionHandler myStepOverHandler;
  private XDebuggerSuspendedActionHandler myStepIntoHandler;
  private XDebuggerSuspendedActionHandler myStepOutHandler;
  private XDebuggerSuspendedActionHandler myForceStepOverHandler;
  private XDebuggerSuspendedActionHandler myForceStepIntoHandler;
  private XDebuggerRunToCursorActionHandler myRunToCursorHandler;
  private XDebuggerRunToCursorActionHandler myForceRunToCursor;
  private XDebuggerActionHandler myResumeHandler;
  private XDebuggerPauseActionHandler myPauseHandler;
  private XDebuggerSuspendedActionHandler myShowExecutionPointHandler;
  private XDebuggerEvaluateActionHandler myEvaluateHandler;
  private XQuickEvaluateHandler myQuickEvaluateHandler;
  private final XDebuggerSettingsPanelProviderImpl mySettingsPanelProvider;
  private DebuggerToggleActionHandler myMuteBreakpointsHandler;

  public XDebuggerSupport() {
    myBreakpointPanelProvider = new XBreakpointPanelProvider();
    myToggleLineBreakpointActionHandler = new XToggleLineBreakpointActionHandler();
    myStepOverHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepOver(false);
      }
    };
    myStepIntoHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepInto();
      }
    };
    myStepOutHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepOut();
      }
    };
    myForceStepOverHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepOver(true);
      }
    };
    myForceStepIntoHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.forceStepInto();
      }
    };
    myRunToCursorHandler = new XDebuggerRunToCursorActionHandler(false);
    myForceRunToCursor = new XDebuggerRunToCursorActionHandler(true);
    myResumeHandler = new XDebuggerActionHandler() {
      protected boolean isEnabled(@NotNull final XDebugSession session, final DataContext dataContext) {
        return session.isPaused();
      }

      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.resume();
      }
    };
    myPauseHandler = new XDebuggerPauseActionHandler();
    myShowExecutionPointHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.showExecutionPoint();
      }
    };
    myMuteBreakpointsHandler = new XDebuggerMuteBreakpointsHandler();
    myEvaluateHandler = new XDebuggerEvaluateActionHandler();
    myQuickEvaluateHandler = new XQuickEvaluateHandler();
    mySettingsPanelProvider = new XDebuggerSettingsPanelProviderImpl();
  }

  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @NotNull
  public DebuggerActionHandler getStepOverHandler() {
    return myStepOverHandler;
  }

  @NotNull
  public DebuggerActionHandler getStepIntoHandler() {
    return myStepIntoHandler;
  }

  @NotNull
  public DebuggerActionHandler getStepOutHandler() {
    return myStepOutHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceStepOverHandler() {
    return myForceStepOverHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceStepIntoHandler() {
    return myForceStepIntoHandler;
  }

  @NotNull
  public DebuggerActionHandler getRunToCursorHandler() {
    return myRunToCursorHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceRunToCursorHandler() {
    return myForceRunToCursor;
  }

  @NotNull
  public DebuggerActionHandler getResumeActionHandler() {
    return myResumeHandler;
  }

  @NotNull
  public DebuggerActionHandler getPauseHandler() {
    return myPauseHandler;
  }

  @NotNull
  public DebuggerActionHandler getToggleLineBreakpointHandler() {
    return myToggleLineBreakpointActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getShowExecutionPointHandler() {
    return myShowExecutionPointHandler;
  }

  @NotNull
  public DebuggerActionHandler getEvaluateHandler() {
    return myEvaluateHandler;
  }

  @NotNull
  public QuickEvaluateHandler getQuickEvaluateHandler() {
    return myQuickEvaluateHandler;
  }

  public DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return myMuteBreakpointsHandler;
  }

  @NotNull
  public DebuggerSettingsPanelProvider getSettingsPanelProvider() {
    return mySettingsPanelProvider;
  }

}
