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
package com.jetbrains.python;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyGotoClassContributor implements ChooseByNameContributor {
  @NotNull
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    Set<String> results = new HashSet<>();
    results.addAll(PyClassNameIndex.allKeys(project));
    results.addAll(PyModuleNameIndex.getAllKeys(project));
    return ArrayUtil.toStringArray(results);
  }

  @NotNull
  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project,
                                         final boolean includeNonProjectItems) {
    final List<NavigationItem> results = new ArrayList<>();
    results.addAll(PyClassNameIndex.find(name, project, includeNonProjectItems));
    results.addAll(PyModuleNameIndex.find(name, project, includeNonProjectItems));
    return results.toArray(new NavigationItem[results.size()]);
  }
}
