package com.intellij.xdebugger.impl.actions;

import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public interface XDebuggerActions {
  @NonNls String VIEW_BREAKPOINTS = "ViewBreakpoints";

  @NonNls String RESUME = "Resume";
  @NonNls String PAUSE = "Pause";

  @NonNls String STEP_OVER = "StepOver";
  @NonNls String STEP_INTO = "StepInto";
  @NonNls String FORCE_STEP_INTO = "ForceStepInto";
  @NonNls String STEP_OUT = "StepOut";

  @NonNls String RUN_TO_CURSOR = "RunToCursor";
  @NonNls String FORCE_RUN_TO_CURSOR = "ForceRunToCursor";

  @NonNls String SHOW_EXECUTION_POINT = "ShowExecutionPoint";
  @NonNls String JUMP_TO_SOURCE = "XDebugger.JumpToSource";

  @NonNls String EVALUATE_EXPRESSION = "EvaluateExpression";

  @NonNls String EVALUATE_DIALOG_TREE_POPUP_GROUP = "XDebugger.Evaluation.Dialog.Tree.Popup";
  @NonNls String INSPECT_TREE_POPUP_GROUP = "XDebugger.Inspect.Tree.Popup";
  @NonNls String VARIABLES_TREE_POPUP_GROUP = "XDebugger.Variables.Tree.Popup";
  @NonNls String WATCHES_TREE_POPUP_GROUP = "XDebugger.Watches.Tree.Popup";
  @NonNls String WATCHES_TREE_TOOLBAR_GROUP = "XDebugger.Watches.Tree.Toolbar";

  @NonNls String XNEW_WATCH = "XDebugger.NewWatch";
  @NonNls String XREMOVE_WATCH = "XDebugger.RemoveWatch";
  @NonNls String XEDIT_WATCH = "XDebugger.EditWatch";

  @NonNls String SET_VALUE = "XDebugger.SetValue";

  @NonNls String MUTE_BREAKPOINTS = "XDebugger.MuteBreakpoints";
}
