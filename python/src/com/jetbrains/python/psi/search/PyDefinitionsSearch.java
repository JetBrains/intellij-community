package com.jetbrains.python.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Query;
import com.jetbrains.python.psi.PyClass;

/**
 * @author yole
 */
public class PyDefinitionsSearch implements QueryExecutor<PsiElement, PsiElement> {
  public boolean execute(final PsiElement queryParameters, final Processor<PsiElement> consumer) {
    if (queryParameters instanceof PyClass) {
      final Query<PyClass> query = PyClassInheritorsSearch.search((PyClass)queryParameters, true);
      return query.forEach(new Processor<PyClass>() {
        public boolean process(final PyClass pyClass) {
          return consumer.process(pyClass);
        }
      });
    }
    return true;
  }
}
