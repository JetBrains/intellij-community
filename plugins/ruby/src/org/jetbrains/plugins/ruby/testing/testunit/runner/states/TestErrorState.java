package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

/**
 * @author Roman Chernyatchik
 */
public class TestErrorState extends TestFailedState {
  public TestErrorState(final String localizedMessage, final String stackTrace) {
    super(localizedMessage, stackTrace);
  }

  public Magnitude getMagnitude() {
    return Magnitude.ERROR_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST ERROR";
  }
}
