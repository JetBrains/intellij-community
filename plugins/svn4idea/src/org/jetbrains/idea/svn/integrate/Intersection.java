/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
* @author Konstantin Kolosovsky.
*/
public class Intersection {
  private final Map<String, String> myLists;
  private final MultiMap<String, Change> myChangesSubset;

  public Intersection() {
    myLists = new HashMap<>();
    myChangesSubset = new MultiMap<>();
  }

  public void add(@NotNull final String listName, @Nullable final String comment, final Change change) {
    myChangesSubset.putValue(listName, change);
    final String commentToPut = comment == null ? listName : comment;
    myLists.put(listName, commentToPut);
  }

  public String getComment(final String listName) {
    return myLists.get(listName);
  }

  public MultiMap<String, Change> getChangesSubset() {
    return myChangesSubset;
  }
}
