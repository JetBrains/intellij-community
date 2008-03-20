package com.jetbrains.python;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

import java.util.Collection;

/**
 * @author yole
 */
public class PyGotoClassContributor implements ChooseByNameContributor {
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(PyClassNameIndex.KEY);
    return classNames.toArray(new String[classNames.size()]);
  }

  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project, final boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? null : GlobalSearchScope.projectScope(project);
    final Collection<PyClass> classes = StubIndex.getInstance().get(PyClassNameIndex.KEY, name, project, scope);
    return classes.toArray(new NavigationItem[classes.size()]);
  }
}
