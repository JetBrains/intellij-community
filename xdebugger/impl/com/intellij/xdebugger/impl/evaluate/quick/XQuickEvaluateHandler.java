package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author nik
 */
public class XQuickEvaluateHandler extends QuickEvaluateHandler {
  public boolean isEnabled(@NotNull final Project project) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null || !session.isSuspended()) {
      return false;
    }
    XSuspendContext context = session.getSuspendContext();
    return context != null && context.getEvaluator() != null;
  }

  public AbstractValueHint createValueHint(@NotNull final Project project, @NotNull final Editor editor, @NotNull final Point point, final ValueHintType type) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) return null;

    XSuspendContext suspendContext = session.getSuspendContext();
    XDebuggerEvaluator evaluator = suspendContext.getEvaluator();
    if (evaluator == null) return null;

    int offset = AbstractValueHint.calculateOffset(editor, point);
    TextRange range = getExpressionRange(evaluator, project, type, editor, offset);
    if (range == null) return null;

    return new XValueHint(project, editor, point, type, range, evaluator, session);
  }

  @Nullable
  private static TextRange getExpressionRange(final XDebuggerEvaluator evaluator, final Project project, final ValueHintType type, final Editor editor,
                                              final int offset) {
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getSelectionStart();
    int selectionEnd = selectionModel.getSelectionEnd();
    if ((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && selectionModel.hasSelection()
        && selectionStart <= offset && offset <= selectionEnd) {
      return new TextRange(selectionStart, selectionEnd);
    }
    return evaluator.getExpressionRangeAtOffset(project, editor.getDocument(), offset);
  }

  public boolean canShowHint(@NotNull final Project project) {
    return isEnabled(project);
  }

  public int getValueLookupDelay(final Project project) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session != null) {
      XSuspendContext suspendContext = session.getSuspendContext();
      if (suspendContext != null) {
        XDebuggerEvaluator evaluator = suspendContext.getEvaluator();
        if (evaluator != null) {
          return evaluator.getValuePopupDelay();
        }
      }
    }
    return 700;
  }
}
