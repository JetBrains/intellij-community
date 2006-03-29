/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.DomElement;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.LocalQuickFix;
import org.jetbrains.annotations.NotNull;

public class DomElementProblemDescriptorImpl implements DomElementProblemDescription {
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
    return myMessage;
  }

  public LocalQuickFix[] getFixes() {
    return myFixes;
  }
}
