/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

public class DomElementProblemDescriptorImpl implements DomElementProblemDescriptor {
  private DomElement myDomElement;
  private ProblemHighlightType myType;
  private String myMessage;
  private LocalQuickFix[] myFixes;

  public DomElementProblemDescriptorImpl(final DomElement domElement,  final String message, final ProblemHighlightType type) {
   this(domElement, message, type, new LocalQuickFix[0]);
  }

  public DomElementProblemDescriptorImpl(final DomElement domElement, final String message, final ProblemHighlightType type, final LocalQuickFix... fixes) {
    myDomElement = domElement;
    myType = type;
    myMessage = message;
    myFixes = fixes;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }

  public ProblemHighlightType getHighlightType() {
    return myType;
  }

  @NotNull
  public String getDescriptionTemplate() {
    return myMessage == null? "": myMessage;
  }

  public LocalQuickFix[] getFixes() {
    return myFixes;
  }


  public int hashCode() {
    if(myDomElement == null) return super.hashCode();

    return 241*getDomElement().hashCode() + 29*getDescriptionTemplate().hashCode() + getHighlightType().hashCode();
  }

  public boolean equals(Object obj) {
    if ((obj instanceof DomElementProblemDescriptor)) return super.equals(obj);
    if (getDomElement() == null) return super.equals(obj);

    DomElementProblemDescriptor other = (DomElementProblemDescriptor)obj;
    return getDomElement().equals(other.getDomElement())
           && getDescriptionTemplate().equals(other.getDescriptionTemplate())
           && getHighlightType().equals(other.getHighlightType());
  }
}
