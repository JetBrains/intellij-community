/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DomElementsProblemsHolderImpl extends SmartList<DomElementProblemDescriptor> implements DomElementsProblemsHolder {
  private HighlightSeverity myDefaultHighlightSeverity = HighlightSeverity.ERROR;

  public void createProblem(DomElement domElement, @Nullable String message) {
    createProblem(domElement, getDefaultHighlightSeverity(), message);
  }

  public void createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message) {
    addProblem(new DomCollectionProblemDescriptorImpl(domElement, message, getDefaultHighlightSeverity(), childDescription));
  }

  @NotNull
  public List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
    List<DomElementProblemDescriptor> problems = new ArrayList<DomElementProblemDescriptor>();
    for (DomElementProblemDescriptor problemDescriptor : this) {
      final DomElement domElement1 = problemDescriptor.getDomElement();
      if (domElement1.equals(domElement)) {
        problems.add(problemDescriptor);
      }
    }
    return problems;
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
    List<DomElementProblemDescriptor> problems = getProblems(domElement);
    if (includeXmlProblems) {
      problems.addAll(getXmlProblems(domElement));
    }

    return problems;
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren) {

    if (!withChildren || domElement == null) return getProblems(domElement);

    final Set<DomElementProblemDescriptor> problems = new HashSet<DomElementProblemDescriptor>();

    domElement.accept(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        problems.addAll(getProblems(element, includeXmlProblems));
        element.acceptChildren(this);
      }
    });

    return new ArrayList<DomElementProblemDescriptor>(problems);
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren,
                                                       HighlightSeverity minSeverity) {
    List<DomElementProblemDescriptor> severityProblem = new ArrayList<DomElementProblemDescriptor>();
    for (DomElementProblemDescriptor problemDescriptor : getProblems(domElement, includeXmlProblems, withChildren)) {
      if (problemDescriptor.getHighlightSeverity().equals(minSeverity)) {
        severityProblem.add(problemDescriptor);
      }
    }

    return severityProblem;
  }

  public void createProblem(DomElement domElement, HighlightSeverity highlightType, String message) {
    addProblem(new DomElementProblemDescriptorImpl(domElement, message, highlightType));
  }

  public void addProblem(final DomElementProblemDescriptor problemDescriptor) {
    add(problemDescriptor);
  }

  private static Collection<DomElementProblemDescriptor> getXmlProblems(DomElement domElement) {
    Collection<DomElementProblemDescriptor> problems = new ArrayList<DomElementProblemDescriptor>();
    if (domElement != null && domElement.getXmlTag() != null) {
      problems.addAll(getResolveProblems(domElement));

    }
    return problems;
  }

  private static Collection<DomElementProblemDescriptor> getResolveProblems(final DomElement domElement) {
    Collection<DomElementProblemDescriptor> problems = new ArrayList<DomElementProblemDescriptor>();
    final XmlElement xmlElement = domElement.getXmlElement();
    if (xmlElement != null) {
      for (PsiReference reference : getReferences(xmlElement)) {
        if (XmlHighlightVisitor.hasBadResolve(reference)) {
          final String description = XmlHighlightVisitor.getErrorDescription(reference);
          problems.add(new DomElementProblemDescriptorImpl(domElement, description, HighlightSeverity.ERROR));
        }
      }
    }
    return problems;
  }

  private static PsiReference[] getReferences(final XmlElement xmlElement) {
    if (xmlElement instanceof XmlAttribute) {
      final XmlAttributeValue value = ((XmlAttribute)xmlElement).getValueElement();
      return value == null ? PsiReference.EMPTY_ARRAY : value.getReferences();
    }
    return xmlElement.getReferences();
  }

  public List<DomElementProblemDescriptor> getAllProblems() {
    return this;
  }

  public HighlightSeverity getDefaultHighlightSeverity() {
    return myDefaultHighlightSeverity;
  }

  public void setDefaultHighlightSeverity(final HighlightSeverity defaultHighlightSeverity) {
    myDefaultHighlightSeverity = defaultHighlightSeverity;
  }
}
