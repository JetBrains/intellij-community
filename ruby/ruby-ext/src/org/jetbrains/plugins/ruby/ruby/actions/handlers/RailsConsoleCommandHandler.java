package org.jetbrains.plugins.ruby.ruby.actions.handlers;

import org.jetbrains.annotations.NotNull;

public class RailsConsoleCommandHandler extends RubyCommandHandler {
  @Override
  public boolean isMatched(@NotNull String commandLine) {
    return commandLine.startsWith("rails console");
  }
}