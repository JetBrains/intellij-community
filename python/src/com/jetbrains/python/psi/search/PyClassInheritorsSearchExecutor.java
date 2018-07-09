// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class PyClassInheritorsSearchExecutor implements QueryExecutor<PyClass, PyClassInheritorsSearch.SearchParameters> {

  /**
   * These base classes are to general to look for inheritors list.
   */
  protected static final ImmutableSet<String> IGNORED_BASES = ImmutableSet.of("object", "BaseException", "Exception");

  public boolean execute(@NotNull final PyClassInheritorsSearch.SearchParameters queryParameters, @NotNull final Processor<? super PyClass> consumer) {
    Set<PyClass> processed = new HashSet<>();
    return processDirectInheritors(queryParameters.getSuperClass(), consumer, queryParameters.isCheckDeepInheritance(), processed);
  }

  private static boolean processDirectInheritors(
      final PyClass superClass, final Processor<? super PyClass> consumer, final boolean checkDeep, final Set<PyClass> processed
  ) {
    return ReadAction.compute(() -> {
      final String superClassName = superClass.getName();
      if (superClassName == null || IGNORED_BASES.contains(superClassName)) {
        return true;  // we don't want to look for inheritors of overly general classes
      }
      if (processed.contains(superClass)) return true;
      processed.add(superClass);
      Project project = superClass.getProject();
      final Collection<PyClass> candidates = StubIndex.getElements(PySuperClassIndex.KEY, superClassName, project,
                                                                   ProjectScope.getAllScope(project), PyClass.class);
      for (PyClass candidate : candidates) {
        final PyClass[] classes = candidate.getSuperClasses(null);
        for (PyClass superClassCandidate : classes) {
          if (superClassCandidate.isEquivalentTo(superClass)) {
            if (!consumer.process(candidate)) {
              return false;
            }
            if (checkDeep && !processDirectInheritors(candidate, consumer, checkDeep, processed)) return false;
            break;
          }
        }
      }
      return true;
    });
  }
}
