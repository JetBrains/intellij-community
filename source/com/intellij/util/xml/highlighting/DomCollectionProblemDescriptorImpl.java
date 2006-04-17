/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.DomElement;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.annotation.HighlightSeverity;

/**
 * @author peter
 */
public class DomCollectionProblemDescriptorImpl extends DomElementProblemDescriptorImpl implements DomCollectionProblemDescriptor {
  private final DomCollectionChildDescription myChildDescription;

  public DomCollectionProblemDescriptorImpl(final DomElement domElement,
                                            final String message,
                                            final HighlightSeverity type,
                                            final DomCollectionChildDescription childDescription) {
    super(domElement, message, type);
    myChildDescription = childDescription;
  }

  public DomCollectionProblemDescriptorImpl(final DomElement domElement,
                                            final String message,
                                            final HighlightSeverity type,
                                            final DomCollectionChildDescription childDescription,
                                            final LocalQuickFix... fixes) {
    super(domElement, message, type, fixes);
    myChildDescription = childDescription;
  }

  public DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }
}
