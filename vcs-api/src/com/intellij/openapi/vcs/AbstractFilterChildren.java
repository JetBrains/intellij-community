package com.intellij.openapi.vcs;

import java.util.List;

public abstract class AbstractFilterChildren<T> {
  protected abstract void sortAscending(final List<T> list);
  protected abstract boolean isAncestor(final T parent, final T child);
  protected void onRemove(final T t) {
  }

  public void doFilter(final List<T> in) {
    sortAscending(in);

    for (int i = 1; i < in.size(); i++) {
      final T child = in.get(i);
      for (int j = i - 1; j >= 0; --j) {
        final T parent = in.get(j);
        if (isAncestor(parent, child)) {
          onRemove(child);
          in.remove(i);
          -- i;
          break;
        }
      }
    }
  }
}
