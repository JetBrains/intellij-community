package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.gem.bundler.actions.AbstractBundlerAction;

import java.util.stream.Stream;

public class RunAnythingBundlerActionGroup extends RunAnythingActionGroup {
  private static final int MAX_BUNDLER_ACTIONS = 2;

  @NotNull
  @Override
  protected String getKey() {
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
    return "Bundler actions";
  }

  @Override
  protected int getMax() {
    return MAX_BUNDLER_ACTIONS;
  }

  @NotNull
  @Override
  public WidgetID getWidget() {
    return WidgetID.BUNDLER;
  }

  @NotNull
  @Override
  protected AnAction[] getActions(Module module) {
    return Stream.of(((DefaultActionGroup)ActionManager.getInstance().getAction("BUNDLER_ACTIONS")).getChildren(null))
                 .filter(action -> action instanceof AbstractBundlerAction).toArray(AnAction[]::new);
  }
}
