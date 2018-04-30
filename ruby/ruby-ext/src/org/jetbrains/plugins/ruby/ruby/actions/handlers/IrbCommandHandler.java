package org.jetbrains.plugins.ruby.ruby.actions.handlers;

import org.jetbrains.annotations.NotNull;

public class IrbCommandHandler extends IrbCommandHandlerBase {
  @Override
  public boolean isMatched(@NotNull String commandLine) {
    return commandLine.equals("irb");
  }
}