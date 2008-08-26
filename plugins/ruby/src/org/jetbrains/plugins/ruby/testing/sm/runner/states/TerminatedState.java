package org.jetbrains.plugins.ruby.testing.sm.runner.states;

/**
 * @author Roman Chernyatchik
 */
public class TerminatedState extends AbstractState {
  public static final TerminatedState INSTANCE = new TerminatedState();

  protected TerminatedState() {
  }

  public boolean isInProgress() {
    return false;
  }

  public boolean isDefect() {
    return true;
  }

  public boolean wasLaunched() {
    return true;
  }

  public boolean isFinal() {
    return true;
  }

  public boolean wasTerminated() {
    return true;
  }

  public Magnitude getMagnitude() {
    return Magnitude.TERMINATED_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TERMINATED";
  }
}
