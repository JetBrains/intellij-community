/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaShortClassNameIndex extends StringStubIndexExtension<PsiClass> {
  public static final StubIndexKey<String,PsiClass> KEY = new StubIndexKey<String, PsiClass>("java.class.shortname");

  private static final JavaShortClassNameIndex ourInstance = new JavaShortClassNameIndex();
  public static JavaShortClassNameIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<String, PsiClass> getKey() {
    return KEY;
  }

  public Collection<PsiClass> get(final String s, final Project project, final GlobalSearchScope scope) {
    return super.get(s, project, new JavaSourceFilterScope(scope, project));
  }

}