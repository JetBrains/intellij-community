// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class QuantitySelection<T> {
  private final @NotNull Group<T> mySelected;
  private final @NotNull Group<T> myUnselected;

  public QuantitySelection(boolean startFromSelectAll) {
    mySelected = new Group<>();
    myUnselected = new Group<>();
    if (startFromSelectAll) {
      mySelected.setAll();
    } else {
      myUnselected.setAll();
    }
  }

  public void add(T t) {
    if (mySelected.hasAll()) {
      myUnselected.remove(t);
    } else {
      mySelected.add(t);
    }
  }

  public void remove(T t) {
    if (mySelected.hasAll()) {
      myUnselected.add(t);
    } else {
      mySelected.remove(t);
    }
  }

  public void clearAll() {
    mySelected.clearAll();
    myUnselected.setAll();
  }

  public void setAll() {
    myUnselected.clearAll();
    mySelected.setAll();
  }

  public @NotNull Set<T> getSelected() {
    return mySelected.getItems();
  }

  public @NotNull Set<T> getUnselected() {
    return myUnselected.getItems();
  }

  public boolean isSelected(T t) {
    return mySelected.hasAll() && !myUnselected.has(t) || myUnselected.hasAll() && mySelected.has(t);
  }

  public boolean areAllSelected() {
    return mySelected.hasAll();
  }

  private static class Group<T> {
    private boolean myAll;
    private final @NotNull Set<T> myItems = new HashSet<>();

    public void add(T t) {
      myItems.add(t);
    }

    public void remove(T t) {
      myAll = false;
      myItems.remove(t);
    }

    public void clearAll() {
      myAll = false;
      myItems.clear();
    }

    public void setAll() {
      myAll = true;
      myItems.clear();
    }

    public @NotNull Set<T> getItems() {
      return myItems;
    }

    public boolean hasAll() {
      return myAll;
    }

    public boolean has(T t) {
      return myItems.contains(t);
    }
  }
}
