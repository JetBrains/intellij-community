package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.edu.PyEduUtils;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PyCCReferenceResolveProvider implements PyReferenceResolveProvider {
  @NotNull
  @Override
  public List<RatedResolveResult> resolveName(@NotNull final PyQualifiedExpression element) {
    Project project = element.getProject();
    if (!CCUtils.isCourseCreator(project)) {
      return Collections.emptyList();
    }
    return PyEduUtils.getResolveResultFromContainingDirectory(element);
  }
}
