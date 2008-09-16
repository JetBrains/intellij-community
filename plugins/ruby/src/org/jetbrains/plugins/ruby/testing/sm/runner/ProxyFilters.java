package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import org.jetbrains.plugins.ruby.testing.sm.runner.states.TestStateInfo;

/**
 * @author Roman Chernyatchik
 */
public interface ProxyFilters {
  Filter FILTER_PASSED = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((SMTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.PASSED_INDEX;
    }
  };
  Filter FILTER_ERRORS = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((SMTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX;
    }
  };
  Filter FILTER_FAILURES = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((SMTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX;
    }
  };
}
