package org.jetbrains.plugins.ruby.testing.sm.runner.ui.statistics;

import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.NullableFunction;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseColumn extends ColumnInfo<SMTestProxy, String> {
  private NullableFunction<List<SMTestProxy>, Object> oldSortFun =
      new NullableFunction<List<SMTestProxy>, Object>() {
        @Nullable
        public Object fun(final List<SMTestProxy> proxies) {
          BaseColumn.super.sort(proxies);

          return null;
        }
      };

  public BaseColumn(String name) {
    super(name);
  }

  @Override
  public void sort(@NotNull final List<SMTestProxy> testProxies) {
    //Invariant: comparator should left Total(initally at row = 0) row as uppermost element!
    StatisticsTableModel.applySortOperation(testProxies, oldSortFun);
  }
}
