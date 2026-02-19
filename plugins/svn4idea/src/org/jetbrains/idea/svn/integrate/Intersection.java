// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private final @NotNull Map<String, String> myListComments = new HashMap<>();
  private final @NotNull Map<String, List<Change>> myChangesByLists = new HashMap<>();

  public void add(@NotNull LocalChangeList list, @NotNull Change change) {
    myChangesByLists.computeIfAbsent(list.getName(), key -> new ArrayList<>()).add(change);
    myListComments.put(list.getName(), notNull(list.getComment(), list.getName()));
  }

  public @NotNull String getComment(@NotNull String listName) {
    return myListComments.get(listName);
  }

  public @NotNull Map<String, List<Change>> getChangesByLists() {
    return myChangesByLists;
  }

  public boolean isEmpty() {
    return myChangesByLists.isEmpty();
  }

  public @NotNull List<Change> getAllChanges() {
    return concat(myChangesByLists.values());
  }

  public static boolean isEmpty(@Nullable Intersection intersection) {
    return intersection == null || intersection.isEmpty();
  }
}
