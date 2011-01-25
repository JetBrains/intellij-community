/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyFunction;

import java.util.Collection;

public class PyFunctionNameIndex extends StringStubIndexExtension<PyFunction> {
  public static final StubIndexKey<String, PyFunction> KEY = StubIndexKey.createIndexKey("Py.function.shortName");

  public StubIndexKey<String, PyFunction> getKey() {
    return KEY;
  }

  public static Collection<PyFunction> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getInstance().get(KEY, name, project, scope);
  }

  public static Collection<PyFunction> find(String name, Project project) {
    return StubIndex.getInstance().get(KEY, name, project, ProjectScope.getAllScope(project));
  }
}