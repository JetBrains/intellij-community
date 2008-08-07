package org.jetbrains.plugins.ruby.testing.testunit.runner.states;

import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.plugins.ruby.RBundle;

/**
 * @author Roman Chernyatchik
 */
public abstract class SuiteFinishedState extends AbstractState {
  //This states are common for all instances and doesn't contains
  //instance-specific information

  public static SuiteFinishedState PASSED_SUITE = new SuiteFinishedState() {
    public Magnitude getMagnitude() {
      return Magnitude.PASSED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "SUITE PASSED";
    }
  };
  public static SuiteFinishedState FAILED_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.FAILED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "FAILED SUITE";
    }
  };

  public static SuiteFinishedState ERROR_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.ERROR_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "ERROR SUITE";
    }
  };

  /**
   * Finished empty test suite
   */
  public static SuiteFinishedState EMPTY_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      //TODO[romeo] add setting to on/off "defect" for this
      return true;
    }

    @Override
    public void printOn(final Printer printer) {
      super.printOn(printer);

      final String msg = RBundle.message("ruby.test.runner.states.suite.error.is.empty") + PrintableTestProxy.NEW_LINE;
      printer.print(msg, ConsoleViewContentType.SYSTEM_OUTPUT);
    }


    public Magnitude getMagnitude() {
      return Magnitude.ERROR_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "EMPTY FINISHED SUITE";
    }
  };

  private SuiteFinishedState() {
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

  public boolean wasTerminated() {
    return false;
  }
}
