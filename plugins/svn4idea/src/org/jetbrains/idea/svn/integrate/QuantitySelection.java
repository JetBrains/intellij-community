/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.integrate;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

public class QuantitySelection<T> {
  @NotNull private final Group<T> mySelected;
  @NotNull private final Group<T> myUnselected;

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

  @NotNull
  public Set<T> getSelected() {
    return mySelected.getItems();
  }

  @NotNull
  public Set<T> getUnselected() {
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
    @NotNull private final Set<T> myItems = newHashSet();

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

    @NotNull
    public Set<T> getItems() {
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
