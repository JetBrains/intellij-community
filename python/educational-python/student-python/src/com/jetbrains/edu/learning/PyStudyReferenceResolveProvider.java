package com.jetbrains.edu.learning;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.edu.PyEduUtils;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PyStudyReferenceResolveProvider implements PyReferenceResolveProvider {
  @NotNull
  @Override
  public List<RatedResolveResult> resolveName(@NotNull final PyQualifiedExpression element,
                                              @NotNull final List<PsiElement> definers) {
    if (StudyTaskManager.getInstance(element.getProject()).getCourse() == null) {
      return Collections.emptyList();
    }
    return PyEduUtils.getResolveResultFromContainingDirectory(element, definers);
  }
}
