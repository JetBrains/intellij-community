package org.jetbrains.plugins.ruby.ruby.actions.handlers;

/**
 * See {@code RubyProcessHandler} for similar methods implementation
 */
public abstract class RubyCommandHandler extends RunAnythingCommandHandler {
  @Override
  public final boolean shouldKillProcessSoftly() {
    return false;
  }
}