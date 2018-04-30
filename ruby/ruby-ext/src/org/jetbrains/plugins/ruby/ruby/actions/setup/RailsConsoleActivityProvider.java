package org.jetbrains.plugins.ruby.ruby.actions.setup;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class RailsConsoleActivityProvider extends RubyConsoleActivityProvider {
  @Override
  public boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern) {
    return StringUtil.equalsIgnoreCase("rails console", pattern);
  }

  @NotNull
  protected String getActionID() {
    return "org.jetbrains.plugins.ruby.rails.console.RunRailsConsoleAction";
  }
}