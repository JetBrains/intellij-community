package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.NullableFunction;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseColumn extends ColumnInfo<RTestUnitTestProxy, String> {
  private NullableFunction<List<RTestUnitTestProxy>, Object> oldSortFun =
      new NullableFunction<List<RTestUnitTestProxy>, Object>() {
        @Nullable
        public Object fun(final List<RTestUnitTestProxy> proxies) {
          BaseColumn.super.sort(proxies);

          return null;
        }
      };

  public BaseColumn(String name) {
    super(name);
  }

  @Override
  public void sort(@NotNull final List<RTestUnitTestProxy> rTestUnitTestProxies) {
    //Invariant: comparator should left Total(initally at row = 0) row as uppermost element!
    RTestUnitStatisticsTableModel.applySortOperation(rTestUnitTestProxies, oldSortFun);
  }
}
