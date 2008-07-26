package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.ui.PrintableTestProxy;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
*/
public interface TestProxyTreeSelectionListener {
  void onSelected(@Nullable final PrintableTestProxy selectedTestProxy);
}
