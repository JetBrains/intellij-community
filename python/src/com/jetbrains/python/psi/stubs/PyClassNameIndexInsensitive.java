package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
public class PyClassNameIndexInsensitive extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String,PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortNameInsensitive");

  @NotNull
  @Override
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project) {
    return StubIndex.getInstance().get(KEY, name.toLowerCase(), project, ProjectScope.getProjectScope(project));
  }
}
