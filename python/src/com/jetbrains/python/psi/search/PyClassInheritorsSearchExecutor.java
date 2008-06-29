package com.jetbrains.python.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;

import java.util.Collection;
import java.util.Set;

/**
 * @author yole
 */
public class PyClassInheritorsSearchExecutor implements QueryExecutor<PyClass, PyClassInheritorsSearch.SearchParameters> {
  public boolean execute(final PyClassInheritorsSearch.SearchParameters queryParameters, final Processor<PyClass> consumer) {
    Set<PyClass> processed = new HashSet<PyClass>();
    return processDirectInheritors(queryParameters.getSuperClass(), consumer, queryParameters.isCheckDeepInheritance(), processed);
  }

  private static boolean processDirectInheritors(final PyClass superClass, final Processor<PyClass> consumer, final boolean checkDeep,
                                                 final Set<PyClass> processed) {
    if (processed.contains(superClass)) return true;
    processed.add(superClass);
    Project project = superClass.getProject();
    final String superClassName = superClass.getName();
    if (superClassName == null) return true;
    final Collection<PyClass> candidates = StubIndex.getInstance().get(PySuperClassIndex.KEY, superClassName, project,
                                                                       ProjectScope.getAllScope(project));
    for(PyClass candidate: candidates) {
      final PyClass[] classes = candidate.getSuperClasses();
      if (classes != null) {
        for(PyClass superClassCandidate: classes) {
          if (superClassCandidate.isEquivalentTo(superClass)) {
            if (!consumer.process(candidate)) {
              return false;
            }
            if (checkDeep && !processDirectInheritors(candidate, consumer, checkDeep, processed)) return false;
            break;
          }
        }
      }
    }
    return true;
  }
}
