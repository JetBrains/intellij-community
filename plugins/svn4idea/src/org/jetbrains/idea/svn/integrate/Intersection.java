// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.concat;

public class Intersection {

  @NotNull private final Map<String, String> myListComments = new HashMap<>();
  @NotNull private final Map<String, List<Change>> myChangesByLists = new HashMap<>();

  public void add(@NotNull LocalChangeList list, @NotNull Change change) {
    myChangesByLists.computeIfAbsent(list.getName(), key -> new ArrayList<>()).add(change);
    myListComments.put(list.getName(), notNull(list.getComment(), list.getName()));
  }

  @NotNull
  public String getComment(@NotNull String listName) {
    return myListComments.get(listName);
  }

  @NotNull
  public Map<String, List<Change>> getChangesByLists() {
    return myChangesByLists;
  }

  public boolean isEmpty() {
    return myChangesByLists.isEmpty();
  }

  @NotNull
  public List<Change> getAllChanges() {
    return concat(myChangesByLists.values());
  }

  public static boolean isEmpty(@Nullable Intersection intersection) {
    return intersection == null || intersection.isEmpty();
  }
}
