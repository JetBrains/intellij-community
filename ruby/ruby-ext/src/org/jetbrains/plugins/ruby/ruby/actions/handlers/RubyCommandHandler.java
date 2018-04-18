package org.jetbrains.plugins.ruby.ruby.actions.handlers;

import com.intellij.ide.actions.runAnything.handlers.RunAnythingCommandHandler;
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