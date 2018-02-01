package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.rails.facet.RailsFacetUtil;
import org.jetbrains.plugins.ruby.tasks.rake.RakeAction;
import org.jetbrains.plugins.ruby.tasks.rake.RakeTaskModuleCache;
import org.jetbrains.plugins.ruby.tasks.rake.task.RakeTask;

import java.util.Arrays;

public class RunAnythingRakeGroup extends RunAnythingActionGroup<RakeAction> {
  private static final int MAX_RAKE = 5;

  @NotNull
  @Override
  protected String getKey() {
    return "run.anything.settings.rake.tasks";
  }

  @NotNull
  @Override
  protected String getPrefix() {
    return "rake";
  }

  @Nullable
  @Override
  protected String getActionText(@NotNull RakeAction action) {
    return action.getTaskFullCmd();
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Rake tasks";
  }

  @Override
  protected int getMax() {
    return MAX_RAKE;
  }

  @NotNull
  @Override
  public WidgetID getWidget() {
    return WidgetID.RAKE;
  }

  @NotNull
  @Override
  protected RakeAction[] getActions(Module module) {
    if (module == null || !RailsFacetUtil.hasRailsSupport(module)) return new RakeAction[0];

    RakeTask rakeTasks = RakeTaskModuleCache.getInstance(module).getRakeTasks();
    if (rakeTasks != null) {
      DataContext dataContext = SimpleDataContext.getSimpleContext(LangDataKeys.MODULE.getName(), module);
      return Arrays.stream(RakeTaskModuleCache.getInstance(module).getRakeActions())
                            .filter(RakeAction.class::isInstance)
                            .map(RakeAction.class::cast)
                            .peek(rakeAction -> rakeAction.updateAction(dataContext, rakeAction.getTemplatePresentation()))
                            .toArray(RakeAction[]::new);
    }

    return new RakeAction[0];
  }
}