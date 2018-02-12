/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.highlighting.*;
import com.intellij.util.xml.impl.DomTestCase;

import java.util.Arrays;

/**
 * @author peter
 */
public class DomAnnotationsTest extends DomTestCase {

  @Override
  protected <T extends DomElement> T createElement(final String xml, final Class<T> aClass) {
    final String name = "a.xml";
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText(name, StdFileTypes.XML, xml, 0,
                                                                                                              true);
    final XmlTag tag = file.getDocument().getRootTag();
    final String rootTagName = tag != null ? tag.getName() : "root";
    final T element = getDomManager().getFileElement(file, aClass, rootTagName).getRootElement();
    assertNotNull(element);
    assertSame(tag, element.getXmlTag());
    return element;
  }

  public void testResolveProblemsAreReportedOnlyOnce() {
    final MyElement myElement = createElement("<a><my-class>abc</my-class></a>", MyElement.class);
    
    new MockDomInspection(MyElement.class).checkFile(DomUtil.getFile(myElement), InspectionManager.getInstance(getProject()), true);
    final DomElementsProblemsHolder holder = DomElementAnnotationsManager.getInstance(getProject()).getProblemHolder(myElement);

    final DomElement element = myElement.getMyClass();
    assertEquals(0, holder.getProblems(myElement).size());
    assertEquals(0, holder.getProblems(myElement).size());
    assertEquals(1, holder.getProblems(element).size());
    assertEquals(1, holder.getProblems(element).size());
    assertEquals(1, holder.getProblems(myElement, true, true).size());
    assertEquals(1, holder.getProblems(myElement, true, true).size());
  }

  public void testMinSeverity() {
    final MyElement element = createElement("<a/>", MyElement.class);
    final DomElementsProblemsHolderImpl holder = new DomElementsProblemsHolderImpl(DomUtil.getFileElement(element));
    final DomElementProblemDescriptorImpl error = new DomElementProblemDescriptorImpl(element, "abc", HighlightSeverity.ERROR);
    final DomElementProblemDescriptorImpl warning = new DomElementProblemDescriptorImpl(element, "abc", HighlightSeverity.WARNING);
    holder.addProblem(error, MockDomInspection.class);
    holder.addProblem(warning, MockDomInspection.class);
    assertEquals(Arrays.asList(error), holder.getProblems(element, true, true, HighlightSeverity.ERROR));
    assertEquals(Arrays.asList(error, warning), holder.getProblems(element, true, true, HighlightSeverity.WARNING));
  }

  public interface MyElement extends DomElement{
    GenericDomValue<PsiClass> getMyClass();
  }

}
