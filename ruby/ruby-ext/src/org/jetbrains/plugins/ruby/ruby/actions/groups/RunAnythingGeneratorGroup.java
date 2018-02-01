package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.rails.actions.generators.actions.GeneratorsActionGroup;

public class RunAnythingGeneratorGroup extends RunAnythingActionGroup<AnAction> {
  private static final int MAX_GENERATORS = 3;

  @NotNull
  @Override
  protected String getKey() {
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
    return "Generators";
  }

  @Override
  protected int getMax() {
    return MAX_GENERATORS;
  }

  @NotNull
  @Override
  public WidgetID getWidget() {
    return WidgetID.GENERATORS;
  }

  @NotNull
  @Override
  protected AnAction[] getActions(Module module) {
    return GeneratorsActionGroup.collectGeneratorsActions(module, false).toArray(AnAction.EMPTY_ARRAY);
  }
}