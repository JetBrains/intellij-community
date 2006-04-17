/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.util.xml.DomElement;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;

public class DomElementProblemDescriptorImpl implements DomElementProblemDescriptor {
  private DomElement myDomElement;
  private HighlightSeverity myType;
  private String myMessage;
  private LocalQuickFix[] myFixes;

  public DomElementProblemDescriptorImpl(final DomElement domElement,  final String message, final HighlightSeverity type) {
   this(domElement, message, type, new LocalQuickFix[0]);
  }

  public DomElementProblemDescriptorImpl(final DomElement domElement, final String message, final HighlightSeverity type, final LocalQuickFix... fixes) {
    myDomElement = domElement;
    myType = type;
    myMessage = message;
    myFixes = fixes;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }

  public HighlightSeverity getHighlightSeverity() {
    return myType;
  }

  @NotNull
  public String getDescriptionTemplate() {
    return myMessage == null? "": myMessage;
  }

  public LocalQuickFix[] getFixes() {
    return myFixes;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DomElementProblemDescriptorImpl that = (DomElementProblemDescriptorImpl)o;

    if (myDomElement != null ? !myDomElement.equals(that.myDomElement) : that.myDomElement != null) return false;
    if (!myMessage.equals(that.myMessage)) return false;
    if (!myType.equals(that.myType)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myDomElement != null ? myDomElement.hashCode() : 0);
    result = 31 * result + myType.hashCode();
    result = 31 * result + myMessage.hashCode();
    return result;
  }
}
