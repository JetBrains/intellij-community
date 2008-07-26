package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

/**
 * @author Roman Chernyatchik
 */
public class TestPassedState extends AbstractState {
  //This state is common for all instances and doesn't contains
  //instance-specific information
  public static final TestPassedState INSTACE = new TestPassedState();

  private TestPassedState() {
  }

  public boolean isInProgress() {
    return false;
  }

  public boolean isDefect() {
    return false;
  }

  public boolean wasLaunched() {
    return true;
  }

  public boolean isFinal() {
    return true;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "PASSED";
  }
}
