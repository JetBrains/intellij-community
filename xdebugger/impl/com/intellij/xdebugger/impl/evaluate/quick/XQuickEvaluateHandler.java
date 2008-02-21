package com.intellij.xdebugger.impl.evaluate.quick;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;

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

  public AbstractValueHint createValueHint(@NotNull final Project project, @NotNull final Editor editor, @NotNull final Point point, final int type) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) return null;

    XSuspendContext suspendContext = session.getSuspendContext();
    XDebuggerEvaluator evaluator = suspendContext.getEvaluator();
    if (evaluator == null) return null;

    TextRange range = evaluator.getSelectedExpressionRange(editor, AbstractValueHint.calculateOffset(editor, point));
    if (range == null) return null;

    return new XValueHint(project, editor, point, type, range, evaluator, session);
  }

  public boolean canShowHint(@NotNull final Project project) {
    return isEnabled(project);
  }

  public int getValueLookupDelay() {
    return 700;//todo[nik] use settings
  }
}
