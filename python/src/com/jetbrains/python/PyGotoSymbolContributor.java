package com.jetbrains.python;

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyGotoSymbolContributor implements GotoClassContributor {
  @NotNull
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    Set<String> symbols = new HashSet<String>();
    symbols.addAll(PyClassNameIndex.allKeys(project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyFunctionNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyVariableNameIndex.KEY, project));
    return ArrayUtil.toStringArray(symbols);
  }

  @NotNull
  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project, final boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? PyProjectScopeBuilder.excludeSdkTestsScope(project)
                                    : GlobalSearchScope.projectScope(project);

    List<NavigationItem> symbols = new ArrayList<NavigationItem>();
    symbols.addAll(PyClassNameIndex.find(name, project, scope));
    symbols.addAll(PyFunctionNameIndex.find(name, project, scope));
    symbols.addAll(PyVariableNameIndex.find(name, project, scope));

    return symbols.toArray(new NavigationItem[symbols.size()]);
  }

  @Override
  public String getQualifiedName(NavigationItem item) {
    if (item instanceof PyQualifiedNameOwner) {
      return ((PyQualifiedNameOwner) item).getQualifiedName();
    }
    return null;
  }

  @Override
  public String getQualifiedNameSeparator() {
    return ".";
  }
}
