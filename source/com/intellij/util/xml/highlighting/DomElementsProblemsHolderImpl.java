/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DomElementsProblemsHolderImpl extends SmartList<DomElementProblemDescription> implements DomElementsProblemsHolder {

  public void createProblem(DomElement domElement, @Nullable String message) {
    createProblem(domElement, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, message);
  }

  @NotNull
  public List<DomElementProblemDescription> getProblems(DomElement domElement) {
    List<DomElementProblemDescription> problems = new ArrayList<DomElementProblemDescription>();
    for (DomElementProblemDescription problemDescription : this) {
      final DomElement domElement1 = problemDescription.getDomElement();
      if (domElement1.equals(domElement)) {
        problems.add(problemDescription);
      }
    }
    return problems;
  }

  public List<DomElementProblemDescription> getProblems(final DomElement domElement, boolean includeXmlProblems) {
    List<DomElementProblemDescription> problems = getProblems(domElement);
    if (includeXmlProblems) {
      problems.addAll(getXmlProblems(domElement));
    }

    return problems;
  }

  public List<DomElementProblemDescription> getProblems(final DomElement domElement,
                                                        final boolean includeXmlProblems,
                                                        final boolean withChildren) {
    if (!withChildren) return getProblems(domElement);

    final Set<DomElementProblemDescription> problems = new HashSet<DomElementProblemDescription>();

    domElement.accept(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        problems.addAll(getProblems(element, includeXmlProblems));
        element.acceptChildren(this);
      }
    });

    return new ArrayList<DomElementProblemDescription>(problems);
  }

  public List<DomElementProblemDescription> getProblems(DomElement domElement, ProblemHighlightType severity) {
    final List<DomElementProblemDescription> problems = new ArrayList<DomElementProblemDescription>();
    for (DomElementProblemDescription problemDescription : this) {
      if (problemDescription.getDomElement().equals(domElement) && problemDescription.getHighlightType().equals(severity)) {
        problems.add(problemDescription);
      }
    }

    return problems;
  }

  public void createProblem(DomElement domElement, ProblemHighlightType highlightType, String message) {
    add(new DomElementProblemDescriptorImpl(domElement, message, highlightType));
  }

  private static Collection<DomElementProblemDescription> getXmlProblems(DomElement domElement) {
    Collection<DomElementProblemDescription> problems = new ArrayList<DomElementProblemDescription>();
    if (domElement.getXmlTag() != null) {
      problems.addAll(getResolveProblems(domElement));

    }
    return problems;
  }

  private static Collection<DomElementProblemDescription> getResolveProblems(final DomElement domElement) {
    Collection<DomElementProblemDescription> problems = new ArrayList<DomElementProblemDescription>();
    if (domElement.getXmlTag() != null) {
      for (PsiReference reference : domElement.getXmlTag().getReferences()) {
        if (XmlHighlightVisitor.hasBadResolve(reference)) {
          problems.add(new DomElementProblemDescriptorImpl(domElement, XmlHighlightVisitor.getErrorDescription(reference),
                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
    }
    return problems;
  }
}
