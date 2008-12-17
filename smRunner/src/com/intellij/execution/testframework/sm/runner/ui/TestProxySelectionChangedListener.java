package com.intellij.execution.testframework.sm.runner.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;

/**
 * @author Roman Chernyatchik
 */
public interface TestProxySelectionChangedListener {
  void onChangeSelection(@Nullable SMTestProxy selectedTestProxy,
                         @NotNull Object sender,
                         boolean requestFocus);
}
