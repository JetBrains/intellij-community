/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml.impl;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PhysicalDomParentStrategy implements DomParentStrategy {
  private XmlElement myElement;
  private final DomManagerImpl myDomManager;

  public PhysicalDomParentStrategy(@NotNull final XmlElement element, DomManagerImpl domManager) {
    myElement = element;
    myDomManager = domManager;
  }

  public DomInvocationHandler getParentHandler() {
    final XmlTag parentTag = getParentTag(myElement);
    assert parentTag != null;
    return myDomManager.getDomHandler(parentTag);
  }

  public static XmlTag getParentTag(final XmlElement xmlElement) {
    return (XmlTag)getParentTagCandidate(xmlElement);
  }

  public static PsiElement getParentTagCandidate(final XmlElement xmlElement) {
    final PsiElement parent = xmlElement.getParent();
    return parent instanceof XmlEntityRef ? parent.getParent() : parent;
  }

  @NotNull
  public final XmlElement getXmlElement() {
    return myElement;
  }

  @NotNull
  public DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    return this;
  }

  @NotNull
  public DomParentStrategy setXmlElement(@NotNull final XmlElement element) {
    myElement = element;
    return this;
  }

  @NotNull
  public DomParentStrategy clearXmlElement() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    return new VirtualDomParentStrategy(parent);
  }

  public String checkValidity() {
    return myElement.isValid() ? null : "Invalid PSI";
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof PhysicalDomParentStrategy)) return false;

    final XmlElement thatElement = ((PhysicalDomParentStrategy)o).myElement;
    if (xmlElementsEqual(myElement, thatElement)) {
      if (myElement != thatElement) {
        //todo remove this assertion before X release
        final PsiElement nav1 = myElement.getNavigationElement();
        final PsiElement nav2 = thatElement.getNavigationElement();
        if (nav1 != nav2) {
          if (ApplicationManagerEx.getApplicationEx().isInternal()) {
            PsiElement cur = findIncluder(myElement);
            PsiElement nav = findIncluder(nav1);
            final PsiElement _nav1 = myElement.getNavigationElement();
            final PsiElement _nav2 = thatElement.getNavigationElement();
            throw new AssertionError(myElement.getText() + "; including=" + (cur == null ? null : cur.getText()) + "; nav=" + (nav == null ? null : nav.getText()));
          }

          throw new AssertionError(nav1.getContainingFile() +
                                ":" +
                                nav1.getTextRange().getStartOffset() +
                                "!=" +
                                nav2.getContainingFile() +
                                ":" +
                                nav2.getTextRange().getStartOffset() +
                                "; " +
                                (nav1 == myElement) +
                                ";" +
                                (nav2 == thatElement));
        }
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiElement findIncluder(PsiElement cur) {
    while (cur != null && !cur.isPhysical()) {
      cur = cur.getParent();
    }
    return cur;
  }

  private static boolean xmlElementsEqual(@NotNull final PsiElement fst, @NotNull final PsiElement snd) {
    if (fst.equals(snd)) return true;

    if (fst.isValid() && fst.isPhysical() || snd.isValid() && snd.isPhysical()) return false;
    if (fst.getTextLength() != snd.getTextLength()) return false;
    if (fst.getStartOffsetInParent() != snd.getStartOffsetInParent()) return false;

    final PsiElement parent1 = fst.getParent();
    final PsiElement parent2 = snd.getParent();
    return parent1 != null && parent2 != null && xmlElementsEqual(parent1, parent2);
  }

  public int hashCode() {
    if (!myElement.isPhysical()) {
      return myElement.getNavigationElement().hashCode();
    }

    return myElement.hashCode();
  }
}
