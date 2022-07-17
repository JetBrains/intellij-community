// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.highlighting.*;
import com.intellij.util.xml.impl.DomTestCase;

import java.util.List;

public class DomAnnotationsTest extends DomTestCase {
  @Override
  protected <T extends DomElement> T createElement(final String xml, final Class<T> aClass) {
    final String name = "a.xml";
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText(name, XmlFileType.INSTANCE, xml, 0, true);
    final XmlTag tag = file.getDocument().getRootTag();
    final String rootTagName = tag != null ? tag.getName() : "root";
    final T element = getDomManager().getFileElement(file, aClass, rootTagName).getRootElement();
    assertNotNull(element);
    assertSame(tag, element.getXmlTag());
    return element;
  }

  public void testResolveProblemsAreReportedOnlyOnce() {
    MyElement myElement = createElement("<a><my-class>abc</my-class></a>", MyElement.class);
    
    new MockDomInspection<>(MyElement.class).checkFile(DomUtil.getFile(myElement), InspectionManager.getInstance(getProject()), true);
    DomElementsProblemsHolder holder = DomElementAnnotationsManager.getInstance(getProject()).getProblemHolder(myElement);

    DomElement element = myElement.getMyClass();
    assertEquals(0, holder.getProblems(myElement).size());
    assertEquals(0, holder.getProblems(myElement).size());
    assertEquals(1, holder.getProblems(element).size());
    assertEquals(1, holder.getProblems(element).size());
    assertEquals(1, holder.getProblems(myElement, true, true).size());
    assertEquals(1, holder.getProblems(myElement, true, true).size());
  }

  public void testMinSeverity() {
    MyElement element = createElement("<a/>", MyElement.class);
    DomElementsProblemsHolderImpl holder = new DomElementsProblemsHolderImpl(DomUtil.getFileElement(element));
    DomElementProblemDescriptorImpl error = new DomElementProblemDescriptorImpl(element, "abc", HighlightSeverity.ERROR);
    DomElementProblemDescriptorImpl warning = new DomElementProblemDescriptorImpl(element, "abc", HighlightSeverity.WARNING);
    holder.addProblem(error, MockDomInspection.getInspection());
    holder.addProblem(warning, MockDomInspection.getInspection());
    assertEquals(List.of(error), holder.getProblems(element, true, true, HighlightSeverity.ERROR));
    assertEquals(List.of(error, warning), holder.getProblems(element, true, true, HighlightSeverity.WARNING));
  }

  public interface MyElement extends DomElement {
    GenericDomValue<PsiClass> getMyClass();
  }
}
