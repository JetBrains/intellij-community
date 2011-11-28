package com.jetbrains.python;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
public class PyGotoClassContributor implements ChooseByNameContributor {
  @NotNull
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    final Collection<String> classNames = PyClassNameIndex.allKeys(project);
    return ArrayUtil.toStringArray(classNames);
  }

  @NotNull
  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project, final boolean includeNonProjectItems) {
    final Collection<PyClass> classes = PyClassNameIndex.find(name, project, includeNonProjectItems);
    return classes.toArray(new NavigationItem[classes.size()]);
  }
}
