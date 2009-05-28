package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
*/
public interface TestProxyTreeSelectionListener {
  /**
   * When element in tree was selected
   * @param selectedTestProxy Selected Element
   * @param viewer
   * @param model
   */
  void onSelected(@Nullable PrintableTestProxy selectedTestProxy,
                  @NotNull TestResultsViewer viewer,
                  @NotNull TestFrameworkRunningModel model);
}
