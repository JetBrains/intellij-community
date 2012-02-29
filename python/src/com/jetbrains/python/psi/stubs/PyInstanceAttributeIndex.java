package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
public class PyInstanceAttributeIndex extends StringStubIndexExtension<PyTargetExpression> {
  public static final StubIndexKey<String, PyTargetExpression> KEY = StubIndexKey.createIndexKey("Py.instanceAttribute.name");

  @NotNull
  @Override
  public StubIndexKey<String, PyTargetExpression> getKey() {
    return KEY;
  }

  public static Collection<PyTargetExpression> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getInstance().get(KEY, name, project, scope);
  }
}
