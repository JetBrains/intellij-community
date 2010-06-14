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
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PyClassNameIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String,PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortName");

  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getInstance().get(KEY, name, project, scope);
  }

  @Nullable
  public static PyClass findClass(@NotNull String qName, Project project, GlobalSearchScope scope) {
    int pos = qName.lastIndexOf(".");
    assert pos > 0;
    String shortName = qName.substring(pos+1);
    for (PyClass pyClass : find(shortName, project, scope)) {
      if (pyClass.getQualifiedName().equals(qName)) {
        return pyClass;
      }
    }
    return null;
  }

  @Nullable
  public static PyClass findClass(@Nullable String qName, Project project) {
    if (qName == null) {
      return null;
    }
    return findClass(qName, project, ProjectScope.getAllScope(project));
  }
}