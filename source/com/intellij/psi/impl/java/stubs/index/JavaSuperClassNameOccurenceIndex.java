/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

import java.util.Collection;

public class JavaSuperClassNameOccurenceIndex extends StringStubIndexExtension<PsiReferenceList> {
  public static final StubIndexKey<String, PsiReferenceList> KEY = new StubIndexKey<String, PsiReferenceList>("java.class.extlist", 998976543232165487L);

  private static final JavaSuperClassNameOccurenceIndex ourInstance = new JavaSuperClassNameOccurenceIndex();
  public static JavaSuperClassNameOccurenceIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<String, PsiReferenceList> getKey() {
    return KEY;
  }

  public Collection<PsiReferenceList> get(final String s, final Project project, final GlobalSearchScope scope) {
    return super.get(s, project, new JavaSourceFilterScope(scope, project));
  }
}