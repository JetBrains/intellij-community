/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaMethodNameIndex extends StringStubIndexExtension<PsiMethod> {
  public static final StubIndexKey<String,PsiMethod> KEY = new StubIndexKey<String, PsiMethod>("java.method.name");

  private static final JavaMethodNameIndex ourInstance = new JavaMethodNameIndex();
  public static JavaMethodNameIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<String, PsiMethod> getKey() {
    return KEY;
  }

  public Collection<PsiMethod> get(final String s, final Project project, final GlobalSearchScope scope) {
    return super.get(s, project, new JavaSourceFilterScope(scope, project));
  }
}