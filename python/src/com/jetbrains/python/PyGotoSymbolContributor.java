package com.jetbrains.python;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyGotoSymbolContributor implements ChooseByNameContributor {
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    Set<String> symbols = new HashSet<String>();
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyClassNameIndex.KEY));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyFunctionNameIndex.KEY));
    return symbols.toArray(new String[symbols.size()]);
  }

  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project, final boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? null : GlobalSearchScope.projectScope(project);

    List<NavigationItem> symbols = new ArrayList<NavigationItem>();
    symbols.addAll(StubIndex.getInstance().get(PyClassNameIndex.KEY, name, project, scope));
    symbols.addAll(StubIndex.getInstance().get(PyFunctionNameIndex.KEY, name, project, scope));

    return symbols.toArray(new NavigationItem[symbols.size()]);
  }
}