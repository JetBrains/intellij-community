package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class MergeActionGroup extends ActionGroup {
  private final MergeOperations myOperations;

  public MergeActionGroup(DiffPanelImpl diffPanel, FragmentSide side) {
    myOperations = new MergeOperations(diffPanel, side);
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    ArrayList<MergeOperations.Operation> operations = myOperations.getOperations();
    AnAction[] actions = new AnAction[operations.size() + 2];
    actions[0] = new SelectSuggestionAction(myOperations);
    actions[1] = Separator.getInstance();
    for (int i = 2; i < actions.length; i++)
      actions[i] = new OperationAction(operations.get(i - 2));
    return actions;
  }

  private static class SelectSuggestionAction extends AnAction {
    private final MergeOperations myOperations;

    public SelectSuggestionAction(MergeOperations operations) {
      super(DiffBundle.message("diff.dialog.select.change.action.name"),
            DiffBundle.message("diff.dialog.select.change.action.description"), null);
      myOperations = operations;
    }

    public void actionPerformed(AnActionEvent e) {
      myOperations.selectSuggestion();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myOperations.getCurrentFragment() != null);
    }
  }

  public static class OperationAction extends AnAction {
    private final MergeOperations.Operation myOperation;

    public OperationAction(MergeOperations.Operation operation) {
      super(operation.getName(), null, operation.getGutterIcon());
      myOperation = operation;
    }

    public void actionPerformed(AnActionEvent e) {
      myOperation.perform(DataKeys.PROJECT.getData(e.getDataContext()));
    }
  }
}
