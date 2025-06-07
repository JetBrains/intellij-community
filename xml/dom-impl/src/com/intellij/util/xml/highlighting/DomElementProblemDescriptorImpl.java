// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DomElementProblemDescriptorImpl implements DomElementProblemDescriptor {
  private static final Logger LOG = Logger.getInstance(DomElementProblemDescriptorImpl.class);
  private final DomElement myDomElement;
  private final HighlightSeverity mySeverity;
  private final @InspectionMessage String myMessage;
  private final LocalQuickFix[] myFixes;
  private Pair<TextRange, PsiElement> myPair;
  static final Pair<TextRange,PsiElement> NO_PROBLEM = new Pair<>(null, null);
  private final ProblemHighlightType myHighlightType;

  public DomElementProblemDescriptorImpl(final @NotNull DomElement domElement, @InspectionMessage String message, final HighlightSeverity type) {
    this(domElement, message, type, LocalQuickFix.EMPTY_ARRAY);
  }

  DomElementProblemDescriptorImpl(final @NotNull DomElement domElement,
                                  @InspectionMessage String message,
                                  final HighlightSeverity type,
                                  @NotNull LocalQuickFix @NotNull ... fixes) {
    this(domElement, message, type, null, null, fixes);
  }

  DomElementProblemDescriptorImpl(final @NotNull DomElement domElement,
                                  @InspectionMessage String message,
                                  final HighlightSeverity type,
                                  final @Nullable TextRange textRange,
                                  ProblemHighlightType highlightType,
                                  @NotNull LocalQuickFix @NotNull ... fixes) {
    myDomElement = domElement;
    final XmlElement element = domElement.getXmlElement();
    if (element != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      //LOG.assertTrue(element.isPhysical(), "Problems may not be created for non-physical DOM elements");
    }
    mySeverity = type;
    myMessage = message;
    myFixes = ArrayUtil.contains(null, fixes) ? ContainerUtil.mapNotNull(fixes, FunctionUtil.id(), LocalQuickFix.EMPTY_ARRAY) : fixes;

    if (textRange != null) {
      final PsiElement psiElement = getPsiElement();
      LOG.assertTrue(psiElement != null, "Problems with explicit text range can't be created for DOM elements without underlying XML element");
      assert psiElement.isValid();
      myPair = Pair.create(textRange, psiElement);
    }
    myHighlightType = highlightType;
  }

  @Override
  public @NotNull DomElement getDomElement() {
    return myDomElement;
  }

  @Override
  public @NotNull HighlightSeverity getHighlightSeverity() {
    return mySeverity;
  }

  @Override
  public @NotNull String getDescriptionTemplate() {
    return myMessage == null ? "" : myMessage;
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getFixes() {
    return myFixes;
  }

  @Override
  public void highlightWholeElement() {
    final PsiElement psiElement = getPsiElement();
    if (psiElement instanceof XmlAttributeValue) {
      assert psiElement.isValid() : psiElement;
      final PsiElement attr = psiElement.getParent();
      myPair = Pair.create(new TextRange(0, attr.getTextLength()), attr);
    }
    else if (psiElement != null) {
      assert psiElement.isValid() : psiElement;
      final XmlTag tag = (XmlTag)(psiElement instanceof XmlTag ? psiElement : psiElement.getParent());
      myPair = new Pair<>(new TextRange(0, tag.getTextLength()), tag);
    }
  }

  Pair<TextRange,PsiElement> getProblemRange() {
    if (myPair == null) {
      myPair = computeProblemRange();
    }
    PsiElement element = myPair.second;
    if (element != null) {
      PsiUtilCore.ensureValid(element);
    }
    return myPair;
  }

  protected @NotNull Pair<TextRange,PsiElement> computeProblemRange() {
    final PsiElement element = getPsiElement();

    if (element != null) {
      assert element.isValid() : element;
      if (element instanceof XmlTag) {
        return DomUtil.getProblemRange((XmlTag)element);
      }

      TextRange range = TextRange.from(0, element.getTextLength());
      if (element instanceof XmlAttributeValue) {
        final String value = ((XmlAttributeValue)element).getValue();
        if (StringUtil.isNotEmpty(value)) {
          range = TextRange.from(element.getText().indexOf(value), value.length());
        }
      }
      return Pair.create(range, element);
    }

    final XmlTag tag = getParentXmlTag();
    if (tag != null) {
      return DomUtil.getProblemRange(tag);
    }
    return NO_PROBLEM;
  }

  @Override
  public String toString() {
    return myDomElement + "; " + myMessage;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DomElementProblemDescriptorImpl that = (DomElementProblemDescriptorImpl)o;

    if (!Objects.equals(myDomElement, that.myDomElement)) return false;
    if (!myMessage.equals(that.myMessage)) return false;
    return mySeverity.equals(that.mySeverity);
  }

  @Override
  public int hashCode() {
    int result;
    result = myDomElement != null ? myDomElement.hashCode() : 0;
    result = 31 * result + mySeverity.hashCode();
    result = 31 * result + myMessage.hashCode();
    return result;
  }

  private @Nullable PsiElement getPsiElement() {
    if (myDomElement instanceof DomFileElement) {
      return ((DomFileElement<?>)myDomElement).getFile();
    }

    if (myDomElement instanceof GenericAttributeValue<?> attributeValue) {
      final XmlAttributeValue value = attributeValue.getXmlAttributeValue();
      return value != null && StringUtil.isNotEmpty(value.getText()) ? value : attributeValue.getXmlElement();
    }
    final XmlTag tag = myDomElement.getXmlTag();
    if (myDomElement instanceof GenericValue && tag != null) {
      final XmlText[] textElements = tag.getValue().getTextElements();
      if (textElements.length > 0) {
        return textElements[0];
      }
    }

    return tag;
  }

  private @Nullable XmlTag getParentXmlTag() {
    DomElement parent = myDomElement.getParent();
    while (parent != null) {
      if (parent.getXmlTag() != null) return parent.getXmlTag();
      parent = parent.getParent();
    }
    return null;
  }

  @Override
  public @Nullable ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }
}
