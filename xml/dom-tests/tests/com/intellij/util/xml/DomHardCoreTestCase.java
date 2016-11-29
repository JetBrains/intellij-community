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

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PresentableLookupValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.impl.DomTestCase;

/**
 * @author peter
 */
public abstract class DomHardCoreTestCase extends CodeInsightTestCase {
  private CallRegistry<DomEvent> myCallRegistry;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCallRegistry = new CallRegistry<>();
    getDomManager().addDomEventListener(new DomEventListener() {
      @Override
      public void eventOccured(DomEvent event) {
        myCallRegistry.putActual(event);
      }
    }, myProject);
  }

  protected DomManagerImpl getDomManager() {
    return DomManagerImpl.getDomManager(getProject());
  }

  protected <T extends DomElement> T createElement(final String xml, final Class<T> aClass) throws IncorrectOperationException {
    return DomTestCase.createElement(getDomManager(), xml, aClass);
  }

  protected void assertEventCount(final int size) {
    assertEquals(myCallRegistry.toString(), size, myCallRegistry.getSize());
  }

  protected void putExpected(final DomEvent event) {
    myCallRegistry.putExpected(event);
  }

  protected void assertResultsAndClear() {
    myCallRegistry.assertResultsAndClear();
  }

  protected PsiReference assertReference(final GenericDomValue value) {
    return assertReference(value, value.getXmlTag());
  }

  protected PsiReference assertReference(final GenericDomValue value, PsiElement resolveTo) {
    final XmlTagValue tagValue = value.getXmlTag().getValue();
    final TextRange textRange = tagValue.getTextRange();
    final String s = value.getStringValue();
    assertNotNull(s);
    final int i = tagValue.getText().indexOf(s);
    return assertReference(value, resolveTo, textRange.getStartOffset() + i + s.length());
  }

  protected PsiReference assertReference(final GenericDomValue value, PsiElement resolveTo, int offset) {
    final XmlTag tag = value.getXmlTag();
    final PsiReference reference = tag.getContainingFile().findReferenceAt(offset);
    assertNotNull(reference);
    reference.getVariants();
    assertEquals(resolveTo, reference.resolve());
    return reference;
  }

  protected PsiReference getReference(final GenericAttributeValue value) {
    final XmlAttributeValue attributeValue = value.getXmlAttributeValue();
    assertNotNull(attributeValue);
    final PsiReference reference = attributeValue.getContainingFile().findReferenceAt(attributeValue.getTextRange().getStartOffset() + 1);
    assertNotNull(reference);
    assertEquals(attributeValue, reference.resolve());
    return reference;
  }

  @SuppressWarnings("deprecation")
  protected void assertVariants(PsiReference reference, String... variants) {
    Object[] refVariants = reference.getVariants();
    assertNotNull(refVariants);
    assertEquals(refVariants.length, variants.length);
    int i = 0;
    for (String variant : variants) {
      Object refVariant = refVariants[i++];
      if (refVariant instanceof LookupElement) {
        assertEquals(variant, ((LookupElement)refVariant).getLookupString());
      }
      else if (refVariant instanceof PresentableLookupValue) {
        assertEquals(variant, ((PresentableLookupValue)refVariant).getPresentation());
      }
      else {
        assertEquals(variant, refVariant.toString());
      }
    }
  }

  protected DomReferencesTest.MyElement createElement(final String xml) throws IncorrectOperationException {
    return createElement(xml, DomReferencesTest.MyElement.class);
  }
}
