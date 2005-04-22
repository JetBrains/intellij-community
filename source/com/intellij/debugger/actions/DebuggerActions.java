/*
 * Interface DebuggerActions
 * @author Jeka
 */
package com.intellij.debugger.actions;

public interface DebuggerActions {
  String RESUME = "Resume";
  String PAUSE = "Pause";
  String SHOW_EXECUTION_POINT = "ShowExecutionPoint";
  String STEP_OVER = "StepOver";
  String STEP_INTO = "StepInto";
  String FORCE_STEP_INTO = "ForceStepInto";
  String STEP_OUT = "StepOut";
  String POP_FRAME = "Debugger.PopFrame";
  String RUN_TO_CURSOR = "RunToCursor";
  String VIEW_BREAKPOINTS = "ViewBreakpoints";
  String EVALUATE_EXPRESSION = "EvaluateExpression";
  String EVALUATION_DIALOG_POPUP = "Debugger.EvaluationDialogPopup";
  String FRAME_PANEL_POPUP = "Debugger.FramePanelPopup";
  String INSPECT_PANEL_POPUP = "Debugger.InspectPanelPopup";
  String THREADS_PANEL_POPUP = "Debugger.ThreadsPanelPopup";
  String WATCH_PANEL_POPUP = "Debugger.WatchesPanelPopup";
  String DEBUGGER_TREE = "DebuggerTree";
  String DEBUGGER_PANEL = "DebuggerPanel";
  String REMOVE_WATCH = "Debugger.RemoveWatch";
  String NEW_WATCH = "Debugger.NewWatch";
  String EDIT_WATCH = "Debugger.EditWatch";
  String SET_VALUE = "Debugger.SetValue";
  String EDIT_FRAME_SOURCE = "Debugger.EditFrameSource";
  String EDIT_NODE_SOURCE = "Debugger.EditNodeSource";
  String MUTE_BREAKPOINTS = "Debugger.MuteBreakpoints";
  String TOGGLE_STEP_SUSPEND_POLICY = "Debugger.ToggleStepThreadSuspendPolicy";
  String REPRESENTATION_LIST = "Debugger.Representation";
}