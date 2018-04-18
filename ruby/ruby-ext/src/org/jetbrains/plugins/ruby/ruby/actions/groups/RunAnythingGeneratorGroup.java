package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.rails.actions.generators.actions.GeneratorsActionGroup;
import org.jetbrains.plugins.ruby.rails.facet.RailsFacetUtil;

import java.util.List;

public class RunAnythingGeneratorGroup extends RunAnythingActionGroup<AnAction> {
  private static final int MAX_GENERATORS = 3;

  @NotNull
  @Override
  public String getVisibilityKey() {
    return "run.anything.settings.generators";
  }

  @NotNull
  @Override
  protected String getPrefix() {
    return "generate";
  }

  @NotNull
  @Override
  public String getTitle() {
    return RBundle.message("run.anything.group.title.generators");
  }

  @Override
  protected int getMaxInitialItems() {
    return MAX_GENERATORS;
  }

  @NotNull
  @Override
  protected List<AnAction> getActions(@Nullable Module module) {
    if (module == null || !RailsFacetUtil.hasRailsSupport(module)) return ContainerUtil.emptyList();

    return GeneratorsActionGroup.collectGeneratorsActions(module, false);
  }
}