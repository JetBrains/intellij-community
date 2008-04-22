/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaFieldNameIndex extends StringStubIndexExtension<PsiField> {
  public static final StubIndexKey<String,PsiField> KEY = new StubIndexKey<String, PsiField>("java.field.name");

  private static final JavaFieldNameIndex ourInstance = new JavaFieldNameIndex();
  public static JavaFieldNameIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<String, PsiField> getKey() {
    return KEY;
  }

  public Collection<PsiField> get(final String s, final Project project, final GlobalSearchScope scope) {
    return super.get(s, project, new JavaSourceFilterScope(scope, project));
  }
}