package org.jetbrains.debugger.memory.view;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

class ExpressionEditorWithHistory extends XDebuggerExpressionEditor {
  ExpressionEditorWithHistory(@NotNull Project project,
                              @NotNull XDebuggerEditorsProvider debuggerEditorsProvider,
                              @Nullable @NonNls String historyId,
                              @Nullable XSourcePosition sourcePosition,
                              @Nullable Disposable parentDisposable) {
    super(project, debuggerEditorsProvider, historyId, sourcePosition, XExpressionImpl.EMPTY_EXPRESSION,
        false, true, true);

    new AnAction("InstancesWindow.ShowHistory") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        showHistory();
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(LookupManager.getActiveLookup(getEditor()) == null);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("DOWN"), getComponent(), parentDisposable);
  }

  private void showHistory() {
    List<XExpression> expressions = getRecentExpressions();
    if (!expressions.isEmpty()) {
      ListPopupImpl historyPopup = new ListPopupImpl(new BaseListPopupStep<XExpression>(null, expressions) {
        @Override
        public PopupStep onChosen(XExpression selectedValue, boolean finalChoice) {
          setExpression(selectedValue);
          requestFocusInEditor();
          return FINAL_CHOICE;
        }
      }) {
        @Override
        protected ListCellRenderer getListElementRenderer() {
          return new ColoredListCellRenderer<XExpression>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList list, XExpression value, int index,
                                                 boolean selected, boolean hasFocus) {
              append(value.getExpression());
            }
          };
        }
      };

      historyPopup.getList().setFont(EditorUtil.getEditorFont());
      historyPopup.showUnderneathOf(getEditorComponent());
    }
  }
}
