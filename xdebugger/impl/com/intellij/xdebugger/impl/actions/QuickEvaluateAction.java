package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class QuickEvaluateAction extends XDebuggerActionBase {
  public QuickEvaluateAction() {
    super(true);
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return new QuickEvaluateHandlerWrapper(debuggerSupport.getQuickEvaluateHandler());
  }

  private static class QuickEvaluateHandlerWrapper extends DebuggerActionHandler {
    private final QuickEvaluateHandler myHandler;

    public QuickEvaluateHandlerWrapper(final QuickEvaluateHandler handler) {
      myHandler = handler;
    }

    public void perform(@NotNull final Project project, final AnActionEvent event) {
      Editor editor = event.getData(PlatformDataKeys.EDITOR);

      if(editor != null) {
        LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
        ValueLookupManager.getInstance(project).showHint(myHandler, editor, editor.logicalPositionToXY(logicalPosition), AbstractValueHint.MOUSE_CLICK_HINT);
      }
    }

    public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
      return myHandler.isEnabled(project);
    }
  }
}
