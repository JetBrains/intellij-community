/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DomElementsProblemsHolderImpl extends SmartList<DomElementProblemDescription> implements DomElementsProblemsHolder {

  public void createProblem(DomElement domElement, @Nullable String message) {
    createProblem(domElement, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, message);
  }

  @NotNull
  public List<DomElementProblemDescription> getProblems(DomElement domElement) {
    List<DomElementProblemDescription> problems = new ArrayList<DomElementProblemDescription>();
    for (DomElementProblemDescription problemDescription : this) {
      final DomElement domElement1 = problemDescription.getDomElement();
      if(domElement1.equals(domElement)) {
        problems.add(problemDescription);
      }
    }
    return problems;
  }

  public List<DomElementProblemDescription> getProblems(DomElement domElement, ProblemHighlightType severity) {
    final List<DomElementProblemDescription> problems = new ArrayList<DomElementProblemDescription>();
    for (DomElementProblemDescription problemDescription : this) {
      if(problemDescription.getDomElement().equals(domElement) && problemDescription.getHighlightType().equals(severity)) {
        problems.add(problemDescription);
      }
    }

    return problems;
  }

  public void createProblem(DomElement domElement, ProblemHighlightType highlightType, String message) {
    add(new DomElementProblemDescriptorImpl(domElement, message, highlightType));
  }
}
