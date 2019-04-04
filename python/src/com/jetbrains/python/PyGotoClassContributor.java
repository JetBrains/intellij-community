// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.Language;
import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyGotoClassContributor implements GotoClassContributor {
  @Override
  @NotNull
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    Set<String> results = new HashSet<>();
    results.addAll(PyClassNameIndex.allKeys(project));
    results.addAll(PyModuleNameIndex.getAllKeys(project));
    return ArrayUtil.toStringArray(results);
  }

  @Override
  @NotNull
  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project,
                                         final boolean includeNonProjectItems) {
    final List<NavigationItem> results = new ArrayList<>();
    results.addAll(PyClassNameIndex.find(name, project, includeNonProjectItems));
    results.addAll(PyModuleNameIndex.find(name, project, includeNonProjectItems));
    return results.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
  }

  @Nullable
  @Override
  public String getQualifiedName(NavigationItem item) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedNameSeparator() {
    return null;
  }

  @Nullable
  @Override
  public Language getElementLanguage() {
    return PythonLanguage.getInstance();
  }
}
