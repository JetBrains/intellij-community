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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.impl.DomTestCase;
import com.intellij.util.xml.ui.DomUIFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class DomSimpleValuesTest extends DomTestCase {

  private MyElement createElement(final String xml) throws IncorrectOperationException {
    return createElement(xml, MyElement.class);
  }

  public void testGetValue() {
    final String text = "<a>foo</a>";
    assertEquals("foo", createElement(text).getTagValue());
    assertEquals("foo", createElement(text).getValue());
  }

  public void testSetValue() {
    final MyElement element = createElement("<a/>");
    assertEquals("", element.getValue());
    element.setValue(239);
    assertEquals("239", element.getValue());
    assertEquals("239", element.getXmlTag().getValue().getText());
    myCallRegistry.putExpected(new DomEvent(element, false));

    myCallRegistry.assertResultsAndClear();
  }

  public void testDefineAndSet() {
    final MyElement element = getDomManager().getFileElement(createXmlFile(""), MyElement.class, "root").getRootElement();
    myCallRegistry.clear();
    assertNull(element.getXmlTag());
    element.setValue(42);
    assertNotNull(element.getXmlTag());
    assertEquals("42", element.getXmlTag().getValue().getText());
    final DomElement element1 = element;
    myCallRegistry.putExpected(new DomEvent(element1, true));
    myCallRegistry.putExpected(new DomEvent(element, false));

    element.setValue((Integer)null);
    assertNull(element.getXmlTag());
    assertEquals(null, element.getValue());

    myCallRegistry.putExpected(new DomEvent(element, false));
    myCallRegistry.assertResultsAndClear();
  }


  public void testSimpleConverters() {
    assertEquals(239, createElement("<a>239</a>").getInt());
    assertEquals(true, createElement("<a>true</a>").getBoolean());
    assertEquals("true", createElement("<a>true</a>").getBuffer().toString());

    assertEquals((short)239, createElement("<a>239</a>").getShort());
    assertEquals(new Long("239"), createElement("<a>239</a>").getLong());
    assertEquals(new Float("239.42"), createElement("<a>239.42</a>").getFloat());
    assertEquals(new BigDecimal("239.42"), createElement("<a>239.42</a>").getBigDecimal());

    final MyElement bigDecimalValue = createElement("<a>239.42</a>");
    bigDecimalValue.setValue(new BigDecimal("111.234"));
    assertEquals("111.234", bigDecimalValue.getValue());

    try {
      createElement("<a>true</a>").getInt();
      fail();
    }
    catch (NullPointerException e) {
    }
    try {
      createElement("<a>42</a>").getBoolean();
      fail();
    }
    catch (NullPointerException e) {
    }
  }

  public void testComment() {
    assertEquals(239, createElement("<a>" +
                                    "  <!-- some comment-->" +
                                    "  239" +
                                    "  <!-- some another comment-->" +
                                    "</a>").getInt());
  }

  public void testPsiClassConverter() {
    final String className = Object.class.getName();
    final PsiClass objectClass = getJavaFacade().findClass(className, GlobalSearchScope.allScope(getProject()));
    assertEquals(objectClass, createElement("<a>" + className + "</a>").getPsiClass());

    assertNull(createElement("<a>abcdef</a>").getPsiClass());
  }

  public void testEnums() {
    final MyElement element = createElement("<a/>", MyElement.class);
    assertNull(element.getEnum());

    element.setEnum(DomTestCase.MyEnum.BAR);
    assertEquals(DomTestCase.MyEnum.BAR, element.getEnum());
    assertEquals(DomTestCase.MyEnum.BAR.getValue(), element.getXmlTag().getValue().getText());

    element.setEnum(null);
    assertNull(element.getEnum());
    assertNull(element.getXmlTag());

    element.setValue(239);
    assertNull(element.getEnum());
  }

  public void testAttributeValues() {
    final MyElement element = createElement("<a attra=\"foo\"/>");
    final GenericAttributeValue<String> attributeValue = element.getAttributeValue();
    assertEquals("attra", attributeValue.getXmlElementName());
    assertEquals("foo", attributeValue.getValue());

    final GenericAttributeValue<Integer> attr = element.getAttr();
    attr.setValue(239);
    assertEquals(239, (int)attr.getValue());
    assertEquals("239", element.getXmlTag().getAttributeValue("attr"));
    final DomElement element1 = attr;
    myCallRegistry.putExpected(new DomEvent(element1, true));
    myCallRegistry.assertResultsAndClear();

    attr.setValue(42);
    myCallRegistry.putExpected(new DomEvent(attr, false));
    myCallRegistry.assertResultsAndClear();

    attr.setValue(null);
    assertNull(attr.getValue());
    assertNull(element.getXmlTag().getAttributeValue("attr"));
    assertNull(element.getXmlTag().getAttribute("attr", ""));
    myCallRegistry.putExpected(new DomEvent(attr, false));
    myCallRegistry.assertResultsAndClear();

    assertEquals("some-attribute", element.getSomeAttribute().getXmlElementName());

    assertNull(createElement("<a attra\"attr\"/>").getAttributeValue().getStringValue());
    assertNull(createElement("<a attra\"\"/>").getAttributeValue().getStringValue());
    assertNull(createElement("<a attra\"/>").getAttributeValue().getStringValue());
  }

  public void testGenericValue() {
    final MyElement element = createElement("<a><generic-child>239</generic-child></a>");
    final GenericDomValue<Integer> integerChild = element.getGenericChild();
    assertEquals(239, (int)integerChild.getValue());
    assertEquals("239", integerChild.getStringValue());
    integerChild.setValue(42);
    assertEquals(42, (int)integerChild.getValue());
    assertEquals("42", integerChild.getStringValue());
  }

  public void testAnnotatedGenericValue() {
    final MyElement element = createElement("<a><buffer>239</buffer></a>");
    element.getGenericChild().getValue();
    final GenericDomValue<StringBuffer> genericChild2 = element.getGenericChild2();
    assertEquals("239", genericChild2.getValue().toString());
  }

  public void testSpecialCharacters() {
    final MyElement element = createElement("");
    element.setValue("<");
    assertEquals("<", element.getValue());
    assertEquals("<![CDATA[<]]>", element.getXmlTag().getValue().getText());

    element.getAttributeValue().setValue("<");
    assertEquals("<", element.getAttributeValue().getValue());
    assertEquals("\"&lt;\"", element.getXmlTag().getAttribute("attra", null).getValueElement().getText());
  }

  public void testIndicators() {
    final MyElement element = createElement("<a><indicator/></a>");
    final GenericDomValue<Boolean> indicator = element.getIndicator();
    assertTrue(indicator.getValue());

    indicator.setValue(false);
    assertFalse(indicator.getValue());
    assertNull(indicator.getStringValue());
    assertNull(indicator.getXmlTag());
    assertEquals(0, element.getXmlTag().getSubTags().length);
    putExpected(new DomEvent(indicator, false));
    assertResultsAndClear();

    indicator.setValue(true);
    assertTrue(indicator.getValue());
    assertEquals("", indicator.getStringValue());
    assertSame(indicator.getXmlTag(), element.getXmlTag().getSubTags()[0]);
    final DomElement element1 = indicator;
    putExpected(new DomEvent(element1, true));
    assertResultsAndClear();

    final XmlTag tag = element.getXmlTag();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        tag.add(createTag("<indicator/>"));
        tag.add(createTag("<indicator/>"));
      }
    }.execute();

    assertTrue(element.isValid());
    assertTrue(element.getIndicator().getValue());
    element.getIndicator().setValue(false);
    assertFalse(element.getIndicator().getValue());
    assertEquals(0, element.getXmlTag().findSubTags("indicator").length);
  }

  public void testConcreteGenericValue() throws Throwable {
    final ConcreteGeneric generic = createElement("", ConcreteGeneric.class);
    generic.setValue("abc");
    assertEquals("abc", generic.getValue());

    DomUIFactory.SET_VALUE_METHOD.invoke(generic, "def");
    assertEquals("def", DomUIFactory.GET_VALUE_METHOD.invoke(generic));
  }

  public void testConcreteGenericValueWithMethods() throws Throwable {
    final ConcreteGenericWithMethods generic = createElement("", ConcreteGenericWithMethods.class);
    generic.setValue("abc");
    assertEquals("abc", generic.getValue());

    DomUIFactory.SET_VALUE_METHOD.invoke(generic, "def");
    assertEquals("def", DomUIFactory.GET_VALUE_METHOD.invoke(generic));
  }

  public void testNameValueInPresentation() {
    final MyElement element = createElement("");
    element.getAttr().setValue(23942);
    assertEquals("23942", element.getPresentation().getElementName());
  }

  public void testResolveToDomElement() {
    final RootInterface element = createElement("", RootInterface.class);
    final MyElement child1 = element.addChild();
    child1.getAttr().setValue(555);
    final MyElement child2 = element.addChild();
    child2.getAttr().setValue(777);

    final GenericDomValue<MyElement> resolve = child2.getResolve();
    resolve.setStringValue("777");
    assertEquals(child2, resolve.getValue());

    resolve.setValue(child1);
    assertEquals("555", resolve.getStringValue());
    assertEquals(child1, resolve.getValue());

    resolve.setStringValue("239");
    assertNull(resolve.getValue());

    final GenericDomValue<MyElement> resolve2 = child2.getResolve2();
    resolve2.setStringValue("777");
    assertEquals(child2, resolve2.getValue());
  }

  public void testPlainPsiTypeConverter() {
    assertNull(createElement("").getPsiType());
    assertSame(PsiType.INT, createElement("<a>int</a>").getPsiType());
    final PsiType psiType = createElement("<a>java.lang.String</a>").getPsiType();
    assertEquals(CommonClassNames.JAVA_LANG_STRING, assertInstanceOf(psiType, PsiClassType.class).getCanonicalText());

    final PsiType arrayType = createElement("<a>int[]</a>").getPsiType();
    assertTrue(arrayType instanceof PsiArrayType);
    assertSame(PsiType.INT, ((PsiArrayType) arrayType).getComponentType());
  }

  public void testJvmPsiTypeConverter() {
    assertNull(createElement("").getJvmPsiType());
    assertNotNull(createElement("<a>int</a>").getJvmPsiType());
    final PsiClassType string = PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject()));
    final PsiType psiType = createElement("<a>java.lang.String</a>").getJvmPsiType();
    assertEquals(CommonClassNames.JAVA_LANG_STRING, assertInstanceOf(psiType, PsiClassType.class).getCanonicalText());

    final PsiArrayType intArray = assertInstanceOf(createElement("<a>[I</a>").getJvmPsiType(), PsiArrayType.class);
    final PsiArrayType stringArray = assertInstanceOf(createElement("<a>[Ljava.lang.String;</a>").getJvmPsiType(), PsiArrayType.class);
    assertSame(PsiType.INT, intArray.getComponentType());
    assertEquals(CommonClassNames.JAVA_LANG_STRING, assertInstanceOf(stringArray.getComponentType(), PsiClassType.class).getCanonicalText());

    assertJvmPsiTypeToString(intArray, "[I");
    assertJvmPsiTypeToString(stringArray, "[Ljava.lang.String;");
    assertJvmPsiTypeToString(string, "java.lang.String");
  }

  public void testValueCaching() {
    final GenericDomValue<String> element = createElement("<a><cached-value/></a>", MyElement.class).getCachedValue();
    assertEquals(0, ((MyConverter) element.getConverter()).fromStringCalls);
    assertEquals("", element.getValue());
    assertEquals(1, ((MyConverter) element.getConverter()).fromStringCalls);
    assertEquals("", element.getValue());
    assertEquals(1, ((MyConverter) element.getConverter()).fromStringCalls);
    element.setValue("1");
    assertEquals(1, ((MyConverter) element.getConverter()).fromStringCalls);
    assertEquals("1", element.getValue());
    assertEquals(2, ((MyConverter) element.getConverter()).fromStringCalls);
  }


  private void assertJvmPsiTypeToString(final PsiType type, final String expected) throws IncorrectOperationException {
    final MyElement element = createElement("");
    element.setJvmPsiType(type);
    assertEquals(expected, element.getValue());
  }

  public void testJavaStyledElement() throws IncorrectOperationException {
    JavaStyledElement element = createElement("<tag javaStyledAttribute=\"666\"></tag>", JavaStyledElement.class);
    assertEquals(element.getJavaStyledAttribute().getXmlElementName(), "javaStyledAttribute");
  }

  public void testGenericValueListConverter() {
    final MyElement element = createElement("<a><string-buffer>abc</string-buffer></a>");
    assertEquals("abc", element.getStringBuffers().get(0).getValue().toString());
  }

  public void testConvertAnnotationOnType() {
    final MyElement element =
      createElement("<a>" + "<my-generic-value>abc</my-generic-value>" + "<my-foo-generic-value>abc</my-foo-generic-value>" + "");
    assertEquals("bar", element.getMyGenericValue().getValue());
    assertEquals("foo", element.getMyFooGenericValue().getValue());
  }
  
  public void testEntities() {
    final MyElement element = createElement("<!DOCTYPE a SYSTEM \"aaa\"\n" +
                                            "[<!ENTITY idgenerator    \"identity\">]>\n" +
                                            "<a attra=\"a&lt;b\" some-attribute=\"&idgenerator;\">&xxx;+&idgenerator;+&amp;</a>");
    assertEquals("a<b", element.getAttributeValue().getValue());
    assertEquals("identity", element.getSomeAttribute().getValue());
//    assertEquals("&xxx;+identity+&", element.getValue());
  }

  public interface RootInterface extends DomElement {
    List<MyElement> getChildren();
    MyElement addChild();
  }


  public interface MyElement extends DomElement {
    @Attribute("attra")
    GenericAttributeValue<String> getAttributeValue();

    @NameValue
    GenericAttributeValue<Integer> getAttr();

    GenericAttributeValue<String> getSomeAttribute();

    String getValue();

    void setValue(Integer value);

    @TagValue()
    int getInt();

    void setValue(String value);

    @TagValue()
    boolean getBoolean();

    @TagValue()
    PsiType getPsiType();

    @Convert(JvmPsiTypeConverter.class)
    @TagValue()
    PsiType getJvmPsiType();

    @TagValue()
    @Convert(JvmPsiTypeConverter.class)
    void setJvmPsiType(PsiType psiType);

    @TagValue()
    DomTestCase.MyEnum getEnum();

    @TagValue
    void setEnum(DomTestCase.MyEnum value);

    @TagValue()
    @Convert(StringBufferConverter.class)
    StringBuffer getBuffer();

    @TagValue()
    PsiClass getPsiClass();

    @TagValue()
    String getTagValue();

    @TagValue
    Long getLong();

    @TagValue
    Float getFloat();

    @TagValue
    short getShort();

    @TagValue
    BigDecimal getBigDecimal();

    void setValue(BigDecimal value);

    GenericDomValue<Integer> getGenericChild();

    @SubTag("buffer")
    @Convert(StringBufferConverter.class)
    GenericDomValue<StringBuffer> getGenericChild2();

    @SubTag(indicator = true)
    GenericDomValue<Boolean> getIndicator();

    @Resolve(MyElement.class)
    GenericDomValue<MyElement> getResolve();

    GenericDomValue<MyElement> getResolve2();

    @Convert(StringBufferConverter.class)
    List<GenericDomValue<StringBuffer>> getStringBuffers();

    @Convert(MyConverter.class) GenericDomValue<String> getCachedValue();

    MyGenericValue getMyGenericValue();

    @Convert(FooConverter.class)
    MyGenericValue getMyFooGenericValue();

  }

  @Convert(BarConverter.class)
  public interface MyGenericValue extends GenericDomValue<String> {
  }

  @NameStrategyForAttributes(JavaNameStrategy.class)
  public interface JavaStyledElement extends DomElement {
    GenericAttributeValue<String> getJavaStyledAttribute();
  }

  public interface ConcreteGeneric extends GenericDomValue<String> {
  }

  public interface ConcreteGenericWithMethods extends GenericDomValue<String> {
    @Override
    String getValue();

    @Override
    void setValue(String s);
  }

  public void testFuhrer() {
    final FieldGroup group = createElement("<field-group>\n" +
                                           "<group-name>myGroup</load-group-name>\n" +
                                           "<field-name>myField1</field-name>\n" +
                                           "<field-name>myField2</field-name>\n" +
                                           "</field-group>",
                                           FieldGroup.class);
    assertEquals(2, group.getFieldNames().size());
    assertEquals("myField1", group.getFieldNames().get(0).getValue().getName().getValue());
    assertEquals(null, group.getFieldNames().get(1).getValue());
  }

  public interface JavaeeModelElement {
  }

  public interface JavaeeDomModelElement extends JavaeeModelElement, DomElement {
}

  public interface FieldGroup extends JavaeeDomModelElement {

    GenericDomValue<String> getGroupName();

    List<GroupField> getFieldNames();

    GroupField addFieldName();
  }

  public interface GroupField extends JavaeeDomModelElement {

    @Convert(value = CmpFieldConverter.class)
    CmpField getValue();

    @Convert(value = CmpFieldConverter.class)
    void setValue(CmpField value);
  }

  public static class CmpFieldConverter extends ResolvingConverter<CmpField> {

    @Override
    public CmpField fromString(String s, ConvertContext context) {
      return ElementPresentationManager.findByName(getVariants(context), s);
    }

    @Override
    public String toString(CmpField t, ConvertContext context) {
      return t == null ? null : t.getName().getValue();
    }

    @Override
    @NotNull
    public Collection<CmpField> getVariants(ConvertContext context) {
      final DomElement element = context.getInvocationElement();
      return Arrays.asList(createCmpField(null, element), createCmpField("myField1", element), createCmpField("def", element));
    }

    private CmpField createCmpField(String name, DomElement element) {
      final CmpField mockElement = element.getManager().createMockElement(CmpField.class, element.getModule(), false);
      mockElement.getName().setValue(name);
      return mockElement;
    }
  }

  public interface CmpField extends JavaeeDomModelElement {
    @NameValue
    public GenericDomValue<String> getName();
  }

  public static class MyConverter extends Converter<String> {
    public int fromStringCalls = 0;

    @Override
    public String fromString(@Nullable @NonNls String s, final ConvertContext context) {
      fromStringCalls++;
      return s;
    }

    @Override
    public String toString(@Nullable String s, final ConvertContext context) {
      return s;
    }
  }

  public static class FooConverter extends Converter<String> {
    @Override
    public String fromString(@Nullable @NonNls final String s, final ConvertContext context) {
      return s == null ? null : "foo";
    }

    @Override
    public String toString(@Nullable final String s, final ConvertContext context) {
      return s;
    }
  }
  public static class BarConverter extends Converter<String> {
    @Override
    public String fromString(@Nullable @NonNls final String s, final ConvertContext context) {
      return s == null ? null : "bar";
    }

    @Override
    public String toString(@Nullable final String s, final ConvertContext context) {
      return s;
    }
  }

}
