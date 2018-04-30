package org.jetbrains.plugins.ruby.ruby.actions.handlers;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.console.config.IrbConsoleBuilder;
import org.jetbrains.plugins.ruby.console.config.IrbRunConfiguration;
import org.jetbrains.plugins.ruby.console.config.IrbRunConfigurationType;

public abstract class IrbCommandHandlerBase extends RubyCommandHandler {
  @Override
  public TextConsoleBuilder getConsoleBuilder(@NotNull Project project) {
    return new IrbConsoleBuilder(project, (IrbRunConfiguration)IrbRunConfigurationType.getInstance().getFactory()
                                                                                      .createTemplateConfiguration(project));
  }
}