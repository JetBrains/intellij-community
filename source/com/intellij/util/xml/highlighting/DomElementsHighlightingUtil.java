/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericValue;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsHighlightingUtil {

  public static List<ProblemDescriptor> getProblemDescriptor(final InspectionManager manager, DomElementProblemDescriptor problemDescriptor) {
    List<ProblemDescriptor>  descritors = new ArrayList<ProblemDescriptor>();
    final DomElement domElement = problemDescriptor.getDomElement();

    if (domElement.getXmlTag() != null) {
      descritors.add(manager.createProblemDescriptor(getPsiElement(domElement), problemDescriptor.getDescriptionTemplate(),
                                             problemDescriptor.getFixes(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
    } else {
      final XmlTag tag = getParentXmlTag(domElement);
      if (tag != null) {
        final ASTNode startNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());
        final ASTNode endNode = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag.getNode());

        descritors.add(manager.createProblemDescriptor(startNode.getPsi(), problemDescriptor.getDescriptionTemplate(), problemDescriptor.getFixes(),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING));

        if (endNode != null && !startNode.equals(endNode)) {
          descritors.add(manager.createProblemDescriptor(endNode.getPsi(), problemDescriptor.getDescriptionTemplate(), problemDescriptor.getFixes(),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING));

        }
      }
    }
    return descritors;
  }


  private static PsiElement getPsiElement(final DomElement domElement) {
    if (domElement instanceof GenericValue && ((GenericValue)domElement).getStringValue() != null &&
        domElement.getXmlTag().getValue().getTextElements().length > 0) {
      return domElement.getXmlTag().getValue().getTextElements()[0];
    }

    return domElement.getXmlTag();
  }

  private static XmlTag getParentXmlTag(final DomElement domElement) {
    DomElement parent = domElement.getParent();
    while (parent != null) {
      if (parent.getXmlTag() != null) return parent.getXmlTag();
    }
    return null;
  }

}
