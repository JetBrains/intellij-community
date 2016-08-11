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

import com.intellij.mock.MockModule;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.impl.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * @author peter
 */
public class DomBasicsTest extends DomTestCase {
  @Override
  protected void invokeTestRunnable(@NotNull final Runnable runnable) throws Exception {
    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        runnable.run();
      }
    }.execute().throwException();
  }

  public void testFileElementCaching() throws Throwable {
    final XmlFile file = createXmlFile("<a/>");
    final DomManagerImpl manager = getDomManager();
    final DomFileElementImpl<DomElement> fileElement = manager.getFileElement(file, DomElement.class, "a");
    assertEquals(fileElement, manager.getFileElement(file, DomElement.class, "a"));
    assertCached(fileElement, file);

    assertEquals(fileElement.getRootElement(), fileElement.getRootElement());
  }

  public void testRootElementUndefineNotExisting() {
    final XmlFile file = createXmlFile("<a/>");
    final DomManagerImpl manager = getDomManager();
    final DomFileElementImpl<DomElement> fileElement = manager.getFileElement(file, DomElement.class, "a");
    final DomElement rootElement = fileElement.getRootElement();
    assertNotNull(rootElement);
    assertTrue(rootElement.exists());

    rootElement.undefine();
    assertFalse(rootElement.exists());
  }

  public void testElementCaching() throws Throwable {
    final MyElement element = createElement("<a><child/></a>");
    assertSame(element.getChild(), element.getChild());
    assertSame(element.getXmlTag().getSubTags()[0], element.getChild().getXmlTag());
    assertCached(element.getChild(), element.getChild().getXmlTag());
  }

  public void testGetParentAndRoot() throws Throwable {
    final XmlFile file = createXmlFile("<a><foo/><child-element/><child-element/></a>");
    final DomFileElementImpl<MyElement> fileElement = getDomManager().getFileElement(file, MyElement.class, "a");
    assertNull(fileElement.getParent());
    assertSame(fileElement, DomUtil.getFileElement(fileElement));

    final MyElement rootElement = fileElement.getRootElement();
    assertSame(fileElement, rootElement.getParent());
    assertSame(fileElement, DomUtil.getFileElement(rootElement));

    assertParent(rootElement.getFoo(), rootElement);
    assertParent(rootElement.getChildElements().get(0), rootElement);
    assertParent(rootElement.getChildElements().get(1), rootElement);
  }

  private static void assertParent(final DomElement element, final DomElement parent) {
    assertEquals(parent, element.getParent());
  }

  public void testEnsureTagExists() throws Throwable {
    final MyElement element = createElement("<a/>");
    myCallRegistry.clear();
    final MyElement child = element.getChild();
    assertNull(child.getXmlTag());

    child.ensureTagExists();
    final XmlTag[] subTags = element.getXmlTag().getSubTags();
    assertEquals(1, subTags.length);
    final XmlTag childTag = subTags[0];
    assertEquals("child", childTag.getName());
    assertCached(child, childTag);
    assertSame(child.getXmlTag(), childTag);

    final DomElement element1 = child;
    myCallRegistry.putExpected(new DomEvent(element1, true));
    myCallRegistry.assertResultsAndClear();

    final MyElement childElement = element.addChildElement();
    final XmlTag childElementTag = childElement.getXmlTag();
    assertSame(childElementTag, childElement.ensureTagExists());
  }

  public void testEnsureRootTagExists() throws Throwable {
    final MyElement rootElement = createEmptyElement();
    myCallRegistry.clear();
    assertNull(rootElement.getXmlTag());
    rootElement.ensureTagExists();
    final DomElement element = rootElement;
    myCallRegistry.putExpected(new DomEvent(element, true));

    assertCached(rootElement, assertRootTag(rootElement));
    myCallRegistry.assertResultsAndClear();
  }

  private XmlTag assertRootTag(final DomElement rootElement) {
    return assertRootTag(DomUtil.getFile(rootElement));
  }

  private static XmlTag assertRootTag(final XmlFile file) {
    final XmlTag rootTag = file.getDocument().getRootTag();
    assertNotNull(rootTag);
    assertEquals("root", rootTag.getName());
    return rootTag;
  }

  protected MyElement createEmptyElement() throws IncorrectOperationException {
    final XmlFile file = createXmlFile("");
    return getDomManager().getFileElement(file, MyElement.class, "root").getRootElement();
  }

  public void testFile() throws Throwable {
    final XmlFile file = createXmlFile("<a>foo</a>");
    DomFileElementImpl<MyElement> fileElement = getDomManager().getFileElement(file, MyElement.class, "a");
    final MyElement rootElement = fileElement.getRootElement();
    assertNotNull(rootElement);
    assertEquals("foo", rootElement.getValue());
  }

  public void testAcceptChildren() throws Throwable {
    final MyElement element = createElement("<a><child-element/><child/><child-element/></a>");
    final Set<DomElement> visited = new HashSet<>();
    element.acceptChildren(new DomElementVisitor() {
      @Override
      public void visitDomElement(DomElement element) {
        visited.add(element);
      }
    });
    final MyElement foo = element.getFoo();
    final MyElement child = element.getChild();
    final MyElement child1 = element.getChildElements().get(0);
    final MyElement child2 = element.getChildElements().get(1);
    final GenericDomValue<Boolean> genericValue = element.getGenericValue();
    assertSameElements(visited, foo, child, child1, child2, genericValue, element.getAttr());
  }

  public void testChildrenReflection() throws Throwable {
    final MyElement element =
      createElement("<a><child/><child-element/><child-element/></a>");
    final DomGenericInfo info = element.getGenericInfo();

    final DomFixedChildDescription foo = info.getFixedChildDescription("foo");
    assertFixedChildDescription(foo, element.getFoo(), "foo");

    final DomFixedChildDescription child = info.getFixedChildDescription("child");
    assertFixedChildDescription(child, element.getChild(), "child");

    final DomFixedChildDescription genericChild = info.getFixedChildDescription("generic-value");
    assertGenericChildDescription(genericChild, element.getGenericValue(), "generic-value");

    final DomCollectionChildDescription collectionChild = info.getCollectionChildDescription("child-element");
    assertEquals(element.getChildElements(), collectionChild.getValues(element));
    assertEquals("child-element", collectionChild.getXmlElementName());
    assertEquals(MyElement.class, collectionChild.getType());
    assertEquals(MyElement.class.getMethod("getChildElements"), collectionChild.getGetterMethod().getMethod());

    assertEquals(new HashSet(Arrays.asList(foo, child, collectionChild, genericChild,
                                           info.getAttributeChildrenDescriptions().get(0))),
                 new HashSet(info.getChildrenDescriptions())
    );
  }

  private void assertFixedChildDescription(final DomFixedChildDescription description,
                                           final DomElement child,
                                           final String tagName) {
    assertEquals(1, description.getCount());
    assertEquals(Arrays.asList(child), description.getValues(child.getParent()));
    assertEquals(tagName, description.getXmlElementName());
    assertEquals(MyElement.class, description.getType());
    assertEquals(JavaMethod.getMethod(MyElement.class, new JavaMethodSignature("get" + StringUtil.capitalize(tagName))), description.getGetterMethod(0));
  }

  private static void assertGenericChildDescription(final DomFixedChildDescription description,
                                             final DomElement child,
                                             final String tagName) {
    assertEquals(1, description.getCount());
    assertEquals(Arrays.asList(child), description.getValues(child.getParent()));
    assertEquals(tagName, description.getXmlElementName());
    assertEquals(GenericDomValue.class, ((ParameterizedType)description.getType()).getRawType());
    assertEquals(JavaMethod.getMethod(MyElement.class, new JavaMethodSignature("getGenericValue")), description.getGetterMethod(0));
  }

  public void testGetDomElementType() throws Throwable {
    final MyElement element = createElement("<a/>");
    assertEquals(MyElement.class.getMethod("getGenericValue").getGenericReturnType(), element.getGenericValue().getDomElementType());
  }

  public void testAddChildrenByReflection() throws Throwable {
    final MyElement element =
      createElement("<a><child-element/></a>");
    final DomGenericInfo info = element.getGenericInfo();
    final DomCollectionChildDescription collectionChild = info.getCollectionChildDescription("child-element");
    final List<? extends DomElement> values = collectionChild.getValues(element);

    MyElement newChild = (MyElement) collectionChild.addValue(element);
    List<DomElement> newChildren = Arrays.asList(values.get(0), newChild);
    assertEquals(newChildren, element.getChildElements());
    assertEquals(newChildren, collectionChild.getValues(element));

    MyElement lastChild = (MyElement) collectionChild.addValue(element, 0);
    newChildren = Arrays.asList(lastChild, values.get(0), newChild);
    assertEquals(newChildren, element.getChildElements());
    assertEquals(newChildren, collectionChild.getValues(element));
  }

  public void testGetPresentableName() throws Throwable {
    assertCollectionPresentableName("Aaas", "aaa", DomNameStrategy.HYPHEN_STRATEGY);
    assertCollectionPresentableName("Aaa Bbbs", "aaa-bbb", DomNameStrategy.HYPHEN_STRATEGY);

    assertCollectionPresentableName("Aaas", "aaa", DomNameStrategy.JAVA_STRATEGY);
    assertCollectionPresentableName("Aaa Children", "aaaChild", DomNameStrategy.JAVA_STRATEGY);

    assertFixedPresentableName("Aaa Bbbs", "aaa-bbbs", DomNameStrategy.HYPHEN_STRATEGY);
    assertFixedPresentableName("Aaa Child", "aaaChild", DomNameStrategy.JAVA_STRATEGY);
  }

  private void assertCollectionPresentableName(final String expected, final String tagName, final DomNameStrategy strategy) {
    assertEquals(expected,
                 new CollectionChildDescriptionImpl(new XmlName(tagName), DomElement.class, null).getCommonPresentableName(strategy));
  }

  private void assertFixedPresentableName(final String expected, final String tagName, final DomNameStrategy strategy) {
    assertEquals(expected, new FixedChildDescriptionImpl(new XmlName(tagName), DomElement.class, 0, new Collection[0]).getCommonPresentableName(strategy));
  }

  public void testNameStrategy() throws Throwable {
    assertTrue(createElement("<a/>").getNameStrategy() instanceof HyphenNameStrategy);

    final AnotherElement anotherElement = createElement("<a/>", AnotherElement.class);
    assertTrue(anotherElement.getNameStrategy() instanceof JavaNameStrategy);
    assertTrue(anotherElement.getChild().getNameStrategy() instanceof JavaNameStrategy);
  }

  public void testModificationCount() throws Throwable {
    final MyElement element = createElement("<a/>");
    final long count = DomUtil.getFileElement(element).getModificationCount();
    element.addChildElement();
    assertTrue(DomUtil.getFileElement(element).getModificationCount() > count);
  }

  public void testIsTagValueElement() throws Throwable {
    assertTrue(getDomManager().getGenericInfo(MyElement.class).isTagValueElement());
    assertFalse(getDomManager().getGenericInfo(AnotherElement.class).isTagValueElement());
  }

  public void testAttributeChildrenGenerics() throws Throwable {
    final StaticGenericInfo genericInfo = DomApplicationComponent.getInstance().getStaticGenericInfo(MyElement.class);
    final List<? extends DomAttributeChildDescription> descriptions = genericInfo.getAttributeChildrenDescriptions();
    assertEquals(1, descriptions.size());
    final DomAttributeChildDescription description = descriptions.get(0);

    final MyElement element = createElement("");
    assertEquals(element.getAttr(), description.getValues(element).get(0));
    assertEquals(element.getAttr(), description.getDomAttributeValue(element));
  }

  private MyElement createElement(final String xml) throws IncorrectOperationException {
    return createElement(xml, MyElement.class);
  }

  public void testSubPropertyAccessing() throws Throwable {
    final MyElement element = createElement("");
    final GenericAttributeValue<String> attr = element.getChild().getChild().getAttr();
    assertNotNull(attr);
    assertEquals(element.getChildChildAttr(), attr);

    final GenericAttributeValue<String> attr1 = element.getChild().addChildElement().getAttr();
    final GenericAttributeValue<String> attr2 = element.getChild().addChildElement().getAttr();
    assertOrderedEquals(element.getChildChildrenAttr(), attr1, attr2);
  }

  public void testInstanceImplementation() throws Throwable {
    MyElement element = createElement("");
    final Object o = new Object();
    element.setObject(o);
    assertSame(o, element.getObject());

    element = createElement("", InheritedElement.class);
    element.setObject(o);
    assertSame(o, element.getObject());
    assertSame(o, element.getObject());
  }

  public void testSeveralInstanceImplementations() throws Throwable {
    InheritedElement element = createElement("", InheritedElement.class);
    final Object o = new Object();
    element.setObject(o);
    assertSame(o, element.getObject());
    assertSame(o, element._getObject());
    element.setString("foo");
    assertEquals("foo", element.getString());
  }

  public void testVisitor() throws Throwable {
    final Integer[] visits = new Integer[]{0, 0, 0};
    DomElementVisitor visitor = new MyVisitor() {
      @Override
      public void visit(InheritedElement element) {
        visits[0]++;
      }

      @Override
      public void visitDomElement(DomElement element) {
        visits[1]++;
      }

      @Override
      public void visitMyElement(MyElement element) {
        visits[2]++;
      }
    };

    createElement("", MyElement.class).accept(visitor);
    final List<Integer> visitsList = Arrays.asList(visits);
    assertEquals(Arrays.asList(0, 0, 1), visitsList);

    createElement("", InheritedElement.class).accept(visitor);
    assertEquals(Arrays.asList(1, 0, 1), visitsList);

    createElement("", AnotherElement.class).accept(visitor);
    assertEquals(Arrays.asList(1, 1, 1), visitsList);

    createElement("", DomElement.class).accept(visitor);
    assertEquals(Arrays.asList(1, 2, 1), visitsList);
    
    createElement("", InheritedElement.class).accept(new DomElementVisitor() {
      @Override
      public void visitDomElement(DomElement element) {
        visits[1]++;
      }
    });
    assertEquals(Arrays.asList(1, 3, 1), visitsList);
  }

  public void testRegisteringImplementation() throws Throwable {
    DomApplicationComponent.getInstance().registerImplementation(AnotherElement.class, EmptyImpl.class, getTestRootDisposable());
    DomApplicationComponent.getInstance().registerImplementation(DomElement.class, BaseImpl.class, getTestRootDisposable());
    final AnotherElement element = createElement("", AnotherElement.class);
    assertTrue(element.getClass().getSuperclass().getName(), element instanceof EmptyImpl);
  }

  public void testMockElements() throws Throwable {
    Module module = new MockModule(getTestRootDisposable());
    final MyElement element = getDomManager().createMockElement(MyElement.class, module, false);
    assertSame(module, element.getModule());
    assertTrue(element.isValid());
    assertNull(element.getXmlTag());
    assertEquals(element, DomUtil.getFileElement(element).getRootElement());
    assertFalse(DomUtil.getFile(element).isPhysical());

    final MyElement element2 = getDomManager().createMockElement(MyElement.class, null, false);
    assertNull(element2.getModule());
    assertNull(element2.getXmlTag());
    element2.addChildElement().getGenericValue().setStringValue("xxx");
    assertNotNull(element2.getXmlTag());
    final MyElement oldChild = element2.getChild();

    element.getAttr().setValue("attr");
    element.getGenericValue().setValue(Boolean.TRUE);
    element.getChild().getGenericValue().setStringValue("abc");
    element.addChildElement().getGenericValue().setStringValue("def");

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        element2.copyFrom(element);
      }
    }.execute();
    assertEquals("attr", element2.getAttr().getValue());
    assertEquals("true", element2.getGenericValue().getStringValue());

    final MyElement newChild = element2.getChild();
    assertEquals("abc", newChild.getGenericValue().getStringValue());

    final List<MyElement> childElements = element2.getChildElements();
    assertEquals(1, childElements.size());
    assertEquals("def", childElements.get(0).getGenericValue().getStringValue());
  }

  public void testCopyingFromEmptyToEmpty() throws Throwable {
    Module module = new MockModule(getTestRootDisposable());
    MyElement element1 = getDomManager().createMockElement(MyElement.class, module, false);
    MyElement element2 = getDomManager().createMockElement(MyElement.class, module, false);
    element2.copyFrom(element1);
    assertNull(element2.getXmlTag());
  }

  public void testCopyingFromNonEmptyToEmpty() throws Throwable {
    Module module = new MockModule(getTestRootDisposable());
    final MyElement element1 = getDomManager().createMockElement(MyElement.class, module, false);
    final MyElement element2 = getDomManager().createMockElement(MyElement.class, module, false);
    element2.ensureTagExists();
    assertNull(element2.getChild().getChild().getGenericValue().getStringValue());
    element1.getChild().getChild().getGenericValue().setStringValue("abc");
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        element2.copyFrom(element1);
      }
    }.execute();
    assertEquals("abc", element2.getChild().getChild().getGenericValue().getStringValue());
  }

  public void testStableValues() throws Throwable {
    final MyElement[] element = new MyElement[]{null};
    final MyElement stable = getDomManager().createStableValue(() -> {
      try {
        return element[0] = createElement("<root/>").addChildElement();
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    });
    assertNotNull(element[0]);
    assertSame(element[0], ((StableElement)stable).getWrappedElement());
    assertEquals(element[0], stable);
    assertEquals(stable, element[0]);
    MyElement oldElement = element[0];
    assertFalse(stable.getChild().equals(stable));
    final GenericDomValue<Boolean> oldGenericValue = stable.getGenericValue();
    assertEquals(oldGenericValue, oldElement.getGenericValue());
    assertEquals(stable.getChild(), oldElement.getChild());
    assertSame(element[0], oldElement);
    final MyElement child1 = stable.addChildElement();
    final MyElement child2 = stable.addChildElement();

    MyElement oldChild1 = oldElement.getChildElements().get(0);
    assertEquals(oldChild1, child1);
    assertEquals(child1, oldChild1);
    final MyElement oldElement1 = oldElement;
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        oldElement1.undefine();
      }
    }.execute();
    assertFalse(oldChild1.isValid());

    assertFalse(oldElement.isValid());
    assertFalse(element[0].isValid());
    assertTrue(stable.isValid());
    assertTrue(element[0].isValid());
    assertNotSame(element[0], oldElement);

    assertFalse(child1.isValid());
    assertFalse(child2.isValid());
    assertEquals(DomUtil.getFileElement(element[0]), DomUtil.getFileElement(stable));
    assertEquals(element[0].getParent(), stable.getParent());

    oldElement = element[0];
    oldChild1 = oldElement.getChild();
    ((StableElement)stable).invalidate();
    assertTrue(oldElement.isValid());
    assertTrue(oldChild1.isValid());
    assertFalse(oldElement.equals(((StableElement)stable).getWrappedElement()));
  }

  public void testStable_Revalidate() throws Throwable {
    final MyElement[] element = new MyElement[]{createElement("")};
    final MyElement stable = getDomManager().createStableValue(() -> element[0]);
    MyElement oldElement = element[0];
    ((StableElement) stable).revalidate();
    assertSame(oldElement, ((StableElement) stable).getWrappedElement());
    
    element[0] = createElement("");
    assertTrue(oldElement.isValid());
    ((StableElement) stable).revalidate();
    assertTrue(oldElement.isValid());
    assertNotSame(oldElement, ((StableElement) stable).getWrappedElement());
    assertSame(element[0], ((StableElement) stable).getWrappedElement());
  }

  public void testStable_Invalidate() throws Throwable {
    final MyElement oldElement = createElement("");
    final MyElement[] element = new MyElement[]{oldElement};
    final MyElement stable = getDomManager().createStableValue(() -> element[0]);
    element[0] = null;
    ((StableElement) stable).invalidate();
    assertTrue(stable.equals(stable));
    assertEquals(oldElement.toString(), stable.toString());
  }

  public void testStableCopies() throws Throwable {
    final MyElement element = createElement("<a><child-element/><child-element><child/></child-element></a>");
    final MyElement parent = element.getChildElements().get(1);
    final MyElement child = parent.getChild();
    final MyElement copy = (MyElement) child.createStableCopy();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        parent.undefine();
        element.addChildElement().getChild().ensureXmlElementExists();
      }
    }.execute();
    assertFalse(child.isValid());
    assertTrue(copy.isValid());
  }

  @Implementation(Impl.class)
  public interface MyElement extends DomElement {
    GenericAttributeValue<String> getAttr();

    String getValue();

    MyElement getFoo();

    MyElement getChild();
    MyElement addChildElement();
    @SuppressWarnings({"UnusedDeclaration"})
    MyElement addChildElement(int index);

    List<MyElement> getChildElements();

    GenericDomValue<Boolean> getGenericValue();

    Object getObject();

    void setObject(Object o);

    @PropertyAccessor({"child", "child", "attr"})
    GenericAttributeValue<String> getChildChildAttr();

    @PropertyAccessor({"child", "childElements", "attr"})
    List<GenericAttributeValue<String>> getChildChildrenAttr();

  }

  public static abstract class Impl implements MyElement {
    private Object myObject;

    public Object _getObject() {
      return myObject;
    }

    @Override
    public Object getObject() {
      return myObject;
    }

    @Override
    public void setObject(final Object object) {
      myObject = object;
    }

  }

  public static abstract class AnotherImpl extends Impl implements InheritedElement{
    private String myString;

    @Override
    public String getString() {
      return myString;
    }

    @Override
    public void setString(final String string) {
      myString = string;
    }
  }

  @NameStrategy(JavaNameStrategy.class)
  public interface AnotherElement extends DomElement {
    DomElement getChild();
  }

  public static abstract class BaseImpl implements DomElement{}

  public static abstract class EmptyImpl extends BaseImpl implements AnotherElement{}

  @Implementation(AnotherImpl.class)
  public interface InheritedElement extends MyElement, DomElement {
    String getString();

    void setString(final String string);

    Object _getObject();
  }

  public interface MyVisitor extends DomElementVisitor{
    void visitMyElement(MyElement element);
    void visit(InheritedElement element);
  }

}
