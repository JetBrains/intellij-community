package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.ide.actions.runAnything.groups.RunAnythingActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.gem.bundler.actions.AbstractBundlerAction;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunAnythingBundlerActionGroup extends RunAnythingActionGroup<AnAction> {
  private static final int MAX_BUNDLER_ACTIONS = 2;

  @NotNull
  @Override
  public String getVisibilityKey() {
    return "run.anything.settings.bundler.actions";
  }

  @NotNull
  @Override
  protected String getPrefix() {
    return "bundle";
  }

  @NotNull
  @Override
  public String getTitle() {
    return RBundle.message("run.anything.group.title.bundler");
  }

  @Override
  protected int getMaxInitialItems() {
    return MAX_BUNDLER_ACTIONS;
  }

  @NotNull
  @Override
  protected List<AnAction> getActions(@Nullable Module module) {
    return Stream.of(((DefaultActionGroup)ActionManager.getInstance().getAction("BUNDLER_ACTIONS")).getChildren(null))
                 .filter(action -> action instanceof AbstractBundlerAction).collect(Collectors.toList());
  }
}
