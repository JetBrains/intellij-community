package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
*/
public class ColumnTest extends ColumnInfo<RTestUnitTestProxy, String> {
  public ColumnTest() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.test.title"));
  }

  public String valueOf(final RTestUnitTestProxy testProxy) {
    return testProxy.getPresentableName();
  }
}
