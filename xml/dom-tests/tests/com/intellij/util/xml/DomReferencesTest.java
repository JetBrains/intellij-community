/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.xml.impl.GenericDomValueReference;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public class DomReferencesTest extends DomHardCoreTestCase {

  public void testMetaData() {
    final MyElement element = createElement("");
    element.getName().setValue("A");
    final XmlTag tag = element.getXmlTag();
    final DomMetaData metaData = assertInstanceOf(tag.getMetaData(), DomMetaData.class);
    assertEquals(tag, metaData.getDeclaration());
    assertOrderedEquals(metaData.getDependences(), DomUtil.getFileElement(element), tag);
    assertEquals("A", metaData.getName());
    assertEquals("A", metaData.getName(null));

    metaData.setName("B");
    assertEquals("B", element.getName().getValue());
  }

  public void testNameReference() {
    final MyElement element = createElement("<a><name>abc</name></a>");
    final DomTarget target = DomTarget.getTarget(element);
    assertNotNull(target);
    final XmlTag tag = element.getName().getXmlTag();
    assertNull(tag.getContainingFile().findReferenceAt(tag.getValue().getTextRange().getStartOffset()));
  }

  public void testProcessingInstruction() {
    createElement("<a><?xml version=\"1.0\"?></a>").getXmlTag().accept(new PsiRecursiveElementVisitor() {
      @Override public void visitElement(PsiElement element) {
        super.visitElement(element);
        for (final PsiReference reference : element.getReferences()) {
          assertFalse(reference instanceof GenericDomValueReference);
        }
      }
    });
  }

  public void testBooleanReference() {
    final MyElement element = createElement("<a><boolean>true</boolean></a>");
    assertVariants(assertReference(element.getBoolean()), "false", "true");
  }

  public void testBooleanAttributeReference() {
    final MyElement element = createElement("<a boolean-attribute=\"true\"/>");
    final PsiReference reference = getReference(element.getBooleanAttribute());
    assertVariants(reference, "false", "true");

    final XmlAttributeValue xmlAttributeValue = element.getBooleanAttribute().getXmlAttributeValue();
    final PsiElement psiElement = reference.getElement();
    assertEquals(xmlAttributeValue, psiElement);

    assertEquals(new TextRange(0, "true".length()).shiftRight(1), reference.getRangeInElement());
  }

  public void testEnumReference() {
    assertVariants(assertReference(createElement("<a><enum>239</enum></a>").getEnum(), null), "A", "B", "C");
    assertVariants(assertReference(createElement("<a><enum>A</enum></a>").getEnum()), "A", "B", "C");
  }

  public void testPsiClass() {
    final MyElement element = createElement("<a><psi-class>java.lang.String</psi-class></a>");
    assertReference(element.getPsiClass(), PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve(),
                    element.getPsiClass().getXmlTag().getValue().getTextRange().getEndOffset() - 1);
  }

  public void testPsiType() {
    final MyElement element = createElement("<a><psi-type>java.lang.String</psi-type></a>");
    assertReference(element.getPsiType(), PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve());
  }

  public void testIndentedPsiType() {
    final MyElement element = createElement("<a><psi-type>  java.lang.Strin   </psi-type></a>");
    final PsiReference psiReference = assertReference(element.getPsiType(), null);
    assertEquals(new TextRange(22, 22 + "Strin".length()), psiReference.getRangeInElement());
  }

  public void testPsiPrimitiveType() {
    final MyElement element = createElement("<a><psi-type>int</psi-type></a>");
    assertReference(element.getPsiType());
  }
  
  public void testPsiPrimitiveTypeArray() {
    final MyElement element = createElement("<a><psi-type>int[]</psi-type></a>");
    final GenericDomValue value = element.getPsiType();
    final XmlTagValue tagValue = value.getXmlTag().getValue();
    final int i = tagValue.getText().indexOf(value.getStringValue());
    assertReference(value, value.getXmlTag(), tagValue.getTextRange().getStartOffset() + i + "int".length());
  }

  public void testPsiUnknownType() {
    final MyElement element = createElement("<a><psi-type>#$^%*$</psi-type></a>");
    assertReference(element.getPsiType(), null);
  }

  public void testPsiArrayType() {
    final MyElement element = createElement("<a><psi-type>java.lang.String[]</psi-type></a>");
    final XmlTag tag = element.getPsiType().getXmlTag();
    final TextRange valueRange = tag.getValue().getTextRange();
    final PsiReference reference = tag.getContainingFile().findReferenceAt(valueRange.getStartOffset() + "java.lang.".length());
    assertNotNull(reference);
    assertEquals(PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve(), reference.resolve());
    assertEquals("<psi-type>java.lang.".length(), reference.getRangeInElement().getStartOffset());
    assertEquals("String".length(), reference.getRangeInElement().getLength());
  }

  public void testJvmArrayType() {
    final MyElement element = createElement("<a><jvm-psi-type>[Ljava.lang.String;</jvm-psi-type></a>");
    final XmlTag tag = element.getJvmPsiType().getXmlTag();
    final TextRange valueRange = tag.getValue().getTextRange();
    final PsiReference reference = tag.getContainingFile().findReferenceAt(valueRange.getEndOffset() - 1);
    assertNotNull(reference);
    assertEquals(PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())).resolve(), reference.resolve());
    assertEquals("<jvm-psi-type>[Ljava.lang.".length(), reference.getRangeInElement().getStartOffset());
    assertEquals("String".length(), reference.getRangeInElement().getLength());
  }

  public void testCustomResolving() {
    final MyElement element = createElement("<a><string-buffer>239</string-buffer></a>");
    assertVariants(assertReference(element.getStringBuffer()), "239", "42", "foo", "zzz");
  }

  public void testAdditionalValues() {
    final MyElement element = createElement("<a><string-buffer>zzz</string-buffer></a>");
    final XmlTag tag = element.getStringBuffer().getXmlTag();
    assertTrue(tag.getContainingFile().findReferenceAt(tag.getValue().getTextRange().getStartOffset()).isSoft());
  }

  public interface MyElement extends DomElement {
    GenericDomValue<Boolean> getBoolean();

    GenericAttributeValue<Boolean> getBooleanAttribute();

    @Convert(MyStringConverter.class)
    GenericDomValue<String> getConvertedString();

    GenericDomValue<MyEnum> getEnum();

    @NameValue GenericDomValue<String> getName();

    GenericDomValue<PsiClass> getPsiClass();

    GenericDomValue<PsiType> getPsiType();

    @Convert(JvmPsiTypeConverter.class)
    GenericDomValue<PsiType> getJvmPsiType();

    List<GenericDomValue<MyEnum>> getEnumChildren();

    @Convert(MyStringBufferConverter.class)
    GenericDomValue<StringBuffer> getStringBuffer();

    MyAbstractElement getChild();

    MyElement getRecursiveChild();

    List<MyGenericValue> getMyGenericValues();

    MyGenericValue getMyAnotherGenericValue();
  }

  @Convert(MyStringConverter.class)
  public interface MyGenericValue extends GenericDomValue<String> {

  }

  public interface MySomeInterface {
    GenericValue<PsiType> getFoo();
  }

  public interface MyAbstractElement extends DomElement {
    GenericAttributeValue<String> getFubar239();
    GenericAttributeValue<Runnable> getFubar();
  }

  public interface MyFooElement extends MyAbstractElement, MySomeInterface {
    @Override
    GenericDomValue<PsiType> getFoo();

    GenericAttributeValue<Set> getFubar2();
  }

  public interface MyBarElement extends MyAbstractElement {
    GenericDomValue<StringBuffer> getBar();
  }


  public enum MyEnum {
    A,B,C
  }

  public static class MyStringConverter extends ResolvingConverter<String> {

    @Override
    @NotNull
    public Collection<String> getVariants(final ConvertContext context) {
      return Collections.emptyList();
    }

    @Override
    public String fromString(final String s, final ConvertContext context) {
      return s;
    }

    @Override
    public String toString(final String s, final ConvertContext context) {
      return s;
    }
  }

  public static class MyStringBufferConverter extends ResolvingConverter<StringBuffer> {

    @Override
    public StringBuffer fromString(final String s, final ConvertContext context) {
      return s == null ? null : new StringBuffer(s);
    }

    @Override
    public String toString(final StringBuffer t, final ConvertContext context) {
      return t == null ? null : t.toString();
    }

    @NotNull
    @Override
    public Collection<StringBuffer> getVariants(final ConvertContext context) {
      return Arrays.asList(new StringBuffer("239"), new StringBuffer("42"), new StringBuffer("foo"));
    }

    @NotNull
    @Override
    public Set<String> getAdditionalVariants(@NotNull ConvertContext context) {
      return Collections.singleton("zzz");
    }
  }
}
