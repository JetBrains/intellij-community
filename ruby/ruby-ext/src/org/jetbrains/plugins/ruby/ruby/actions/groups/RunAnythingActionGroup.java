package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingAction;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingActionItem;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingSearchListModel;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingUtil;

public abstract class RunAnythingActionGroup<T extends AnAction> extends RunAnythingGroup {
  @NotNull
  protected abstract String getPrefix();

  @Nullable
  protected String getActionText(@NotNull T action) {
    return null;
  }

  @NotNull
  protected abstract T[] getActions(Module module);

  @Override
  public RunAnythingAction.SearchResult getItems(@NotNull Project project,
                                                 @Nullable Module module,
                                                 @NotNull RunAnythingSearchListModel listModel,
                                                 @NotNull String pattern,
                                                 boolean isMore,
                                                 @NotNull Runnable check) {

    final RunAnythingAction.SearchResult result = new RunAnythingAction.SearchResult();

    check.run();
    for (T action : getActions(module)) {
      String actionText = getActionText(action);
      RunAnythingActionItem actionItem = new RunAnythingActionItem(action, actionText == null ? ObjectUtils
        .notNull(action.getTemplatePresentation().getText(), "Undefined action") : actionText);

      if (addToList(listModel, result, pattern, actionItem, getPrefix() + " " + RunAnythingUtil.getPresentationText(action), isMore)) break;
      check.run();
    }

    return result;
  }
}