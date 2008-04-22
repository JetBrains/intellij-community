/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.IntStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaFullClassNameIndex extends IntStubIndexExtension<PsiClass> {
  public static final StubIndexKey<Integer,PsiClass> KEY = new StubIndexKey<Integer, PsiClass>("java.class.fqn");

  private static final JavaFullClassNameIndex ourInstance = new JavaFullClassNameIndex();
  public static JavaFullClassNameIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<Integer, PsiClass> getKey() {
    return KEY;
  }

  public Collection<PsiClass> get(final Integer integer, final Project project, final GlobalSearchScope scope) {
    return super.get(integer, project, new JavaSourceFilterScope(scope, project));
  }
}