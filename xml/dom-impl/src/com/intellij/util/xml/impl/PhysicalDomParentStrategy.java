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
package com.intellij.util.xml.impl;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PhysicalDomParentStrategy implements DomParentStrategy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.PhysicalDomParentStrategy");
  private XmlElement myElement;
  private final DomManagerImpl myDomManager;

  public PhysicalDomParentStrategy(@NotNull final XmlElement element, DomManagerImpl domManager) {
    myElement = element;
    myDomManager = domManager;
  }

  @Override
  public DomInvocationHandler getParentHandler() {
    final XmlTag parentTag = getParentTag(myElement);
    assert parentTag != null;
    return myDomManager.getDomHandler(parentTag);
  }

  @Nullable
  public static XmlTag getParentTag(final XmlElement xmlElement) {
    return (XmlTag)getParentTagCandidate(xmlElement);
  }

  @Nullable
  public static PsiElement getParentTagCandidate(final XmlElement xmlElement) {
    final PsiElement parent = xmlElement.getParent();
    return parent instanceof XmlEntityRef ? parent.getParent() : parent;
  }

  @Override
  @NotNull
  public final XmlElement getXmlElement() {
    return myElement;
  }

  @Override
  @NotNull
  public DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    return this;
  }

  @Override
  @NotNull
  public DomParentStrategy setXmlElement(@NotNull final XmlElement element) {
    myElement = element;
    return this;
  }

  @Override
  public String toString() {
    return "Physical:" + myElement;
  }

  @Override
  @NotNull
  public DomParentStrategy clearXmlElement() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    return new VirtualDomParentStrategy(parent);
  }

  @Override
  public String checkValidity() {
    return myElement.isValid() ? null : "Invalid PSI";
  }

  @Override
  public XmlFile getContainingFile(DomInvocationHandler handler) {
    return DomImplUtil.getFile(handler);
  }

  @Override
  public boolean isPhysical() {
    return true;
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  public boolean equals(final Object o) {
    return strategyEquals(this, o);
  }

  public static boolean strategyEquals(DomParentStrategy strategy, final Object o) {

    if (strategy == o) return true;
    if (!(o instanceof DomParentStrategy)) return false;
    final XmlElement thatElement = ((DomParentStrategy)o).getXmlElement();
    if (thatElement == null) return false;
    XmlElement element = strategy.getXmlElement();
    if (element == null) return false;

    if (xmlElementsEqual(element, thatElement)) {
      if (element != thatElement) {
        final PsiElement nav1 = element.getNavigationElement();
        final PsiElement nav2 = thatElement.getNavigationElement();
        if (nav1 != nav2) {
          PsiElement curContext = findIncluder(element);
          PsiElement navContext = findIncluder(nav1);
          LOG.error(LogMessageEx.createEvent(
            "x:include processing error",
            "nav1,nav2=" + nav1 + ", " + nav2 + ";\n" +
            nav1.getContainingFile() + ":" + nav1.getTextRange().getStartOffset() + "!=" + nav2.getContainingFile() + ":" + nav2.getTextRange().getStartOffset() + ";\n" +
            (nav1 == element) + ";" + (nav2 == thatElement) + ";\n" +
            "contexts equal: " +  (curContext == navContext) + ";\n" +
            "curContext?.physical=" + (curContext != null && curContext.isPhysical()) + ";\n" +
            "navContext?.physical=" + (navContext != null && navContext.isPhysical()) + ";\n" +
            "myElement.physical=" + element.isPhysical() + ";\n" +
            "thatElement.physical=" + thatElement.isPhysical() + "\n" + DebugUtil.currentStackTrace(),
            new Attachment("Including tag text 1.xml", curContext == null ? "null" : curContext.getText()),
            new Attachment("Including tag text 2.xml", navContext == null ? "null" : navContext.getText())
          ));
          throw new AssertionError();
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

    PsiElement nav1 = fst.getNavigationElement();
    PsiElement nav2 = snd.getNavigationElement();
    return nav1 != null && nav1.equals(nav2);
  }

  public int hashCode() {
    if (!myElement.isPhysical()) {
      return myElement.getNavigationElement().hashCode();
    }

    return myElement.hashCode();
  }
}
