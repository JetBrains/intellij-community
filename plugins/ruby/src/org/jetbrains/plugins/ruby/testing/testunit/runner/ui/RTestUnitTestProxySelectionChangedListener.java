package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public interface RTestUnitTestProxySelectionChangedListener {
  void onSelected(@Nullable final RTestUnitTestProxy selectedTestProxy,
                  final boolean requestFocus);
}
