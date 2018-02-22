package org.jetbrains.plugins.ruby.ruby.actions.handlers;

import org.jetbrains.plugins.ruby.ruby.run.RubyProcessHandler;

/**
 * See {@link RubyProcessHandler} for similar methods implementation
 */
public abstract class RubyCommandHandler extends RunAnythingCommandHandler {
  @Override
  public final boolean shouldKillProcessSoftly() {
    return false;
  }
}