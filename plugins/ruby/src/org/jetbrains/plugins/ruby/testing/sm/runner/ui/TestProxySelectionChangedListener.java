package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

/**
 * @author Roman Chernyatchik
 */
public interface TestProxySelectionChangedListener {
  void onChangeSelection(@Nullable final SMTestProxy selectedTestProxy,
                         final boolean requestFocus);
}
