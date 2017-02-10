/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ParameterizedTypeImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.impl.DomTestCase;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class DomExtensionsTest extends DomTestCase {
  private static final Key<Boolean> BOOL_KEY = Key.create("aaa");

  public void testExtendAttributes() throws Throwable {
    registerDomExtender(AttrDomExtender.class);
    assertEmpty(getCustomChildren(createElement("<a foo=\"xxx\"/>", MyElement.class)));

    MyElement element = createElement("<a attr=\"foo\" foo=\"true\"/>", MyElement.class);
    final GenericAttributeValue child = assertInstanceOf(assertOneElement(getCustomChildren(element)), GenericAttributeValue.class);
    assertEquals("true", child.getStringValue());
    assertEquals(Boolean.TRUE, child.getValue());
    assertEquals(Boolean.class, DomUtil.getGenericValueParameter(child.getDomElementType()));
    assertSame(element.getXmlTag().getAttribute("foo"), child.getXmlElement());

    child.setStringValue("xxx");
    assertEquals("xxx", child.getStringValue());
    assertEquals("xxx", element.getXmlTag().getAttributeValue("foo"));

    element = createElement("<a attr=\"foo\" foo=\"true\"/>", MyElement.class);
    final GenericAttributeValue value = getDomManager().getDomElement(element.getXmlTag().getAttribute("foo"));
    assertNotNull(value);
    assertEquals(value, assertOneElement(getCustomChildren(element)));
    assertNotNull(element.getGenericInfo().getAttributeChildDescription("foo"));
  }

  public void testCustomAttributeChildClass() throws Throwable {
    registerDomExtender(AttrDomExtender3.class);
    final MyElement element = createElement("<a attr=\"xxx\"/>", MyElement.class);
    assertEquals("xxx", assertInstanceOf(assertOneElement(getCustomChildren(element)), MyAttribute.class).getXmlElementName());
  }

  public void testUserData() throws Throwable {
    registerDomExtender(AttrDomExtender3.class);
    final MyElement element = createElement("<a attr=\"xxx\"/>", MyElement.class);
    final DomAttributeChildDescription description = element.getGenericInfo().getAttributeChildDescription("xxx");
    assertNotNull(description);
    assertSame(Boolean.TRUE, description.getUserData(BOOL_KEY));
  }

  public void testUseCustomConverter() throws Throwable {
    registerDomExtender(AttrDomExtender2.class);
    final MyElement myElement = createElement("<a attr=\"xxx\" xxx=\"zzz\" yyy=\"zzz\"/>", MyElement.class);
    assertUnorderedCollection(getCustomChildren(myElement), element -> {
      final StringBuffer stringBuffer = ((GenericAttributeValue<StringBuffer>)element).getValue();
      assertEquals("zzz", stringBuffer.toString());
      assertInstanceOf(((GenericAttributeValue<StringBuffer>)element).getConverter(), StringBufferConverter.class);
      assertNotNull(myElement.getGenericInfo().getAttributeChildDescription("xxx"));

      Convert convert = element.getAnnotation(Convert.class);
      assertNotNull(convert);
      assertEquals(StringBufferConverter.class, convert.value());
      assertTrue(convert.soft());
    }, element -> {
      final StringBuffer stringBuffer = ((GenericAttributeValue<StringBuffer>)element).getValue();
      assertEquals("zzz", stringBuffer.toString());
      assertInstanceOf(((GenericAttributeValue<StringBuffer>)element).getConverter(), StringBufferConverter.class);
      assertNotNull(myElement.getGenericInfo().getAttributeChildDescription("yyy"));

      Convert convert = element.getAnnotation(Convert.class);
      assertNotNull(convert);
      assertEquals(StringBufferConverter.class, convert.value());
      assertFalse(convert.soft());
    });
  }

  public void testFixedChildren() throws Throwable {
    registerDomExtender(FixedDomExtender.class);
    final MyElement myElement = createElement("<a attr=\"xxx\"><xxx>zzz</xxx><yyy attr=\"foo\"/><yyy attr=\"bar\"/></a>", MyElement.class);
    assertUnorderedCollection(getCustomChildren(myElement), element -> {
      assertEquals(GenericDomValue.class, ReflectionUtil.getRawType(element.getDomElementType()));
      final StringBuffer stringBuffer = ((GenericDomValue<StringBuffer>)element).getValue();
      assertEquals("zzz", stringBuffer.toString());
      assertInstanceOf(((GenericDomValue<StringBuffer>)element).getConverter(), MyStringBufferConverter.class);
      assertNotNull(myElement.getGenericInfo().getFixedChildDescription("xxx"));

      Convert convert = element.getAnnotation(Convert.class);
      assertNotNull(convert);
      assertEquals(MyStringBufferConverter.class, convert.value());
      assertTrue(convert.soft());

      assertNotNull(element.getGenericInfo().getAttributeChildDescription("aaa"));
    }, element -> {
      assertEquals("foo", ((MyElement) element).getAttr().getValue());
      assertNull(element.getAnnotation(Convert.class));
    }, element -> {
      assertEquals("bar", ((MyElement) element).getAttr().getValue());
      assertNull(element.getAnnotation(Convert.class));
    });
    final DomFixedChildDescription description = myElement.getGenericInfo().getFixedChildDescription("yyy");
    assertNotNull(description);
    assertEquals(2, description.getCount());
  }

  public void testCollectionChildren() throws Throwable {
    registerDomExtender(CollectionDomExtender.class);
    final MyElement myElement = createElement("<a attr=\"xxx\"><xxx>zzz</xxx><xxx attr=\"foo\"/></a>", MyElement.class);
    assertUnorderedCollection(getCustomChildren(myElement), element -> {
      assertEquals("foo", ((MyElement) element).getAttr().getValue());
      assertNull(element.getAnnotation(Convert.class));
    }, element -> {
      assertNull(((MyElement) element).getAttr().getValue());
      assertNull(element.getAnnotation(Convert.class));
    });
    assertNotNull(myElement.getGenericInfo().getCollectionChildDescription("xxx"));
  }

  public void testCollectionAdders() throws Throwable {
    registerDomExtender(CollectionDomExtender.class);
    final MyElement myElement = createElement("<a attr=\"xxx\"></a>", MyElement.class);
    final DomCollectionChildDescription description = myElement.getGenericInfo().getCollectionChildDescription("xxx");
    final DomElement element2 = description.addValue(myElement);
    final DomElement element0 = description.addValue(myElement, 0);
    final DomElement element3 = description.addValue(myElement, MyConcreteElement.class);
    final DomElement element1 = description.addValue(myElement, MyConcreteElement.class, 1);
    assertSameElements(getCustomChildren(myElement), element0, element1, element2, element3);
  }

  public void testCustomChildrenAccessFromExtender() throws Throwable {
    registerDomExtender(MyCustomChildrenElement.class, CustomDomExtender.class);
    final MyCustomChildrenElement myElement = createElement("<a><xx/><yy/><concrete-child/><some-concrete-child/></a>", MyCustomChildrenElement.class);
    final DomCollectionChildDescription description = myElement.getGenericInfo().getCollectionChildDescription("xx");
    assertNotNull(description);
    assertInstanceOf(assertOneElement(description.getValues(myElement)), MyDynamicElement.class);
    assertInstanceOf(assertOneElement(myElement.getCustomChidren()), MyCustomElement.class);
    assertInstanceOf(assertOneElement(myElement.getConcreteChildren()), MyConcreteElement.class);
    assertNotNull(assertInstanceOf(myElement.getSomeConcreteChild(), MyConcreteElement.class).getXmlTag());
  }

  public void testFirstChildRedefinitionOnExtending() throws Exception {
    registerDomExtender(MyCustomChildrenElement.class, ModestDomExtender.class);

    final MyCustomChildrenElement myElement = createElement("<a><concrete-child/><concrete-child/></a>", MyCustomChildrenElement.class);
    final List<MyConcreteElement> list = myElement.getConcreteChildren();
    final List<MyConcreteElement> list2 = myElement.getConcreteChildren();
    assertSame(list.get(0), list2.get(0));
    assertSame(list.get(1), list2.get(1));
  }

  public static List<DomElement> getCustomChildren(final DomElement element) {
    final List<DomElement> children = new ArrayList<>();
    element.acceptChildren(new DomElementVisitor() {
      @Override
      public void visitDomElement(final DomElement element) {
        if (!"attr".equals(element.getXmlElementName())) {
          children.add(element);
        }
      }
    });
    return children;
  }

  public void registerDomExtender(Class<? extends DomExtender<MyElement>> extender) {
    registerDomExtender(MyElement.class, extender);
  }

  public <T extends DomElement> void registerDomExtender(final Class<T> domClass, final Class<? extends DomExtender<T>> extenderClass) {
    final DomExtenderEP extenderEP = new DomExtenderEP();
    extenderEP.domClassName = domClass.getName();
    extenderEP.extenderClassName = extenderClass.getName();
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), DomExtenderEP.EP_NAME, extenderEP, getTestRootDisposable());
  }


  public interface MyElement extends DomElement {
    GenericAttributeValue<String> getAttr();
  }

  public interface MyCustomChildrenElement extends DomElement {
    @CustomChildren List<MyCustomElement> getCustomChidren();

    List<MyConcreteElement> getConcreteChildren();

    MyConcreteElement getSomeConcreteChild();
  }

  public interface MyConcreteElement extends MyElement {
  }
  public interface MyCustomElement extends MyElement {
  }
  public interface MyDynamicElement extends MyElement {
  }

  public static class AttrDomExtender extends DomExtender<MyElement> {
    @Override
    public void registerExtensions(@NotNull final MyElement element, @NotNull final DomExtensionsRegistrar registrar) {
      final String value = element.getAttr().getValue();
      if (value != null) {
        registrar.registerGenericAttributeValueChildExtension(new XmlName(value), Boolean.class);
      }
    }
  }

  public static class AttrDomExtender2 extends DomExtender<MyElement> {
    @Override
    public void registerExtensions(@NotNull final MyElement element, @NotNull final DomExtensionsRegistrar registrar) {
      final String value = element.getAttr().getValue();
      if (value != null) {
        registrar.registerGenericAttributeValueChildExtension(new XmlName(value), StringBuffer.class).setConverter(new StringBufferConverter(), true);
        registrar.registerGenericAttributeValueChildExtension(new XmlName("yyy"), StringBuffer.class).setConverter(new StringBufferConverter(), false);
      }
    }
  }



  public static class AttrDomExtender3 extends DomExtender<MyElement> {

    @Override
    public void registerExtensions(@NotNull final MyElement element, @NotNull final DomExtensionsRegistrar registrar) {
      final String value = element.getAttr().getValue();
      if (value != null) {
        registrar.registerAttributeChildExtension(new XmlName(value), MyAttribute.class).putUserData(BOOL_KEY, Boolean.TRUE);
      }
    }
  }

  public static class FixedDomExtender extends DomExtender<MyElement> {
    @Override
    public void registerExtensions(@NotNull final MyElement element, @NotNull final DomExtensionsRegistrar registrar) {
      final String value = element.getAttr().getValue();
      if (value != null) {
        final ParameterizedType type = new ParameterizedTypeImpl(GenericDomValue.class, StringBuffer.class);
        final DomExtension extension =
          registrar.registerFixedNumberChildExtension(new XmlName(value), type).setConverter(new MyStringBufferConverter(true), true);
        extension.addExtender(new DomExtender<GenericDomValue<StringBuffer>>(){

          @Override
          public void registerExtensions(@NotNull final GenericDomValue<StringBuffer> stringBufferGenericDomValue, @NotNull final DomExtensionsRegistrar registrar) {
            registrar.registerGenericAttributeValueChildExtension(new XmlName("aaa"), String.class);
          }
        });
        ((DomExtensionsRegistrarImpl)registrar).registerFixedNumberChildrenExtension(new XmlName("yyy"), MyElement.class, 2);
      }
    }
  }
  public static class CollectionDomExtender extends DomExtender<MyElement> {
    @Override
    public void registerExtensions(@NotNull final MyElement element, @NotNull final DomExtensionsRegistrar registrar) {
      final String value = element.getAttr().getValue();
      if (value != null) {
        registrar.registerCollectionChildrenExtension(new XmlName(value), MyElement.class).setConverter(new MyStringBufferConverter(true), true);
      }
    }
  }

  public static class CustomDomExtender extends DomExtender<MyCustomChildrenElement> {
    @Override
    public void registerExtensions(@NotNull final MyCustomChildrenElement element, @NotNull final DomExtensionsRegistrar registrar) {
      assertEmpty(element.getCustomChidren());
      registrar.registerCollectionChildrenExtension(new XmlName("xx"), MyDynamicElement.class);
    }
  }

  public static class ModestDomExtender extends DomExtender<MyCustomChildrenElement> {
    @Override
    public void registerExtensions(@NotNull final MyCustomChildrenElement element, @NotNull final DomExtensionsRegistrar registrar) {
      registrar.registerCollectionChildrenExtension(new XmlName("xx"), MyDynamicElement.class);
    }
  }

  public static class MyStringBufferConverter extends StringBufferConverter {
    public MyStringBufferConverter(boolean b) {
    }
  }
  public static interface MyAttribute extends GenericAttributeValue<Boolean> {}
}

