package com.intellij.codeInsight.highlighting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlComment;

/**
 * @author yole
 */
public class XmlReadWriteAccessDetector implements ReadWriteAccessDetector {
  public boolean isReadWriteAccessible(final PsiElement element) {
    return element instanceof XmlAttributeValue ||
        element instanceof XmlTag ||
        element instanceof XmlElementDecl ||
        element instanceof XmlComment; // e.g. <!--@elvariable name="xxx" type="yyy"-->
  }

  public boolean isDeclarationWriteAccess(final PsiElement element) {
    return false;
  }

  public Access getReferenceAccess(final PsiElement referencedElement, final PsiReference reference) {
    PsiElement refElement = reference.getElement();
    return ( refElement instanceof XmlAttributeValue &&
              (!(referencedElement instanceof XmlTag) || refElement.getParent().getParent() == referencedElement)
            ) ||
            refElement instanceof XmlElementDecl ||
            refElement instanceof XmlComment   // e.g. <!--@elvariable name="xxx" type="yyy"-->
           ? Access.Write : Access.Read;

  }

  public Access getExpressionAccess(final PsiElement expression) {
    return expression instanceof XmlAttributeValue ? Access.Write : Access.Read;
  }
}
