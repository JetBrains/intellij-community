package com.jetbrains.python.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;

import java.util.Collection;

/**
 * @author yole
 */
public class PyClassInheritorsSearchExecutor implements QueryExecutor<PyClass, PyClassInheritorsSearch.SearchParameters> {
  public boolean execute(final PyClassInheritorsSearch.SearchParameters queryParameters, final Processor<PyClass> consumer) {
    PyClass superClass = queryParameters.getSuperClass();
    Project project = superClass.getProject();
    final Collection<PyClass> candidates = StubIndex.getInstance().get(PySuperClassIndex.KEY, superClass.getName(), project,
                                                                       ProjectScope.getAllScope(project));
    for(PyClass candidate: candidates) {
      final PyClass[] classes = candidate.getSuperClasses();
      if (classes != null) {
        for(PyClass superClassCandidate: classes) {
          if (superClassCandidate.isEquivalentTo(superClass)) {
            if (!consumer.process(candidate)) {
              return false;
            }
            else {
              break;
            }
          }
        }
      }
    }
    return true;
  }
}
