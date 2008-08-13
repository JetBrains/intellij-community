package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;

/**
 * @author Roman Chernyatchik
*/
public class ColumnDuration extends ColumnInfo<RTestUnitTestProxy, String> {
  public ColumnDuration() {
    super(RBundle.message("ruby.test.runner.ui.tabs.statistics.columns.duration.title"));
  }

  public String valueOf(final RTestUnitTestProxy testProxy) {
    return TestsPresentationUtil.getDurationPresentation(testProxy);
  }

  //TODO sort
}
