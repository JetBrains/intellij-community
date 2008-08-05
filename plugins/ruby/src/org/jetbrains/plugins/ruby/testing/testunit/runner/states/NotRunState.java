package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

/**
   * Default state for tests. Tes hasn't been started yet.
 */
public class NotRunState extends AbstractState {
  private static final NotRunState INSTANCE = new NotRunState();

  private NotRunState() {
  }

  /**
   * This state is common for all instances and doesn't contains
   * instance-specific information
   * @return
   */
  public static NotRunState getInstance() {
    return INSTANCE;
  }

  public boolean isInProgress() {
    return false;
  }

  //TODO[romeo] if wan't run is it deffect or not?   May be move it to settings
  public boolean isDefect() {
    return false;
  }

  public boolean wasLaunched() {
    return false;
  }

  public boolean isFinal() {
    return false;
  }

  public boolean wasTerminated() {
    return false;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "NOT RUN";
  }
}
