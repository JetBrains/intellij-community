package com.intellij.openapi.command;

/**
 * @author max
 */
public abstract class CommandProcessorEx extends CommandProcessor {
  public abstract void enterModal();
  public abstract void leaveModal();
}
