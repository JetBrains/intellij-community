/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlTag;

/**
 * @author yole
 */
public class XmlReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(final PsiElement element) {
    return element instanceof XmlAttributeValue ||
        element instanceof XmlTag ||
        element instanceof XmlElementDecl ||
        element instanceof XmlComment; // e.g. <!--@elvariable name="xxx" type="yyy"-->
  }

  @Override
  public boolean isDeclarationWriteAccess(final PsiElement element) {
    return false;
  }

  @Override
  public Access getReferenceAccess(final PsiElement referencedElement, final PsiReference reference) {
    PsiElement refElement = reference.getElement();
    return refElement instanceof XmlAttributeValue &&
           (!(referencedElement instanceof XmlTag) || refElement.getParent().getParent() == referencedElement) ||
            refElement instanceof XmlElementDecl ||
            refElement instanceof XmlComment   // e.g. <!--@elvariable name="xxx" type="yyy"-->
           ? Access.Write : Access.Read;

  }

  @Override
  public Access getExpressionAccess(final PsiElement expression) {
    return expression instanceof XmlAttributeValue ? Access.Write : Access.Read;
  }
}
