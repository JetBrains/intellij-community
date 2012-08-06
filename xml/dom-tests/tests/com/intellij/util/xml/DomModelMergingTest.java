/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.command.WriteCommandAction;

import java.util.*;

/**
 * @author peter
 */
public class DomModelMergingTest extends DomTestCase{
  private ModelMerger myMerger;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myMerger = new ModelMergerImpl();
  }

  public void testVisitor() throws Throwable {
    final MyElement element1 = createElement("", MyElement.class);
    final MyElement foo1 = element1.getFoo();
    final MyElement bar1 = element1.addBar();
    final MyElement bar2 = element1.addBar();

    final MyElement element2 = createElement("", MyElement.class);
    final MyElement foo2 = element2.getFoo();
    final MyElement bar3 = element2.addBar();

    final MyElement element = myMerger.mergeModels(MyElement.class, element1, element2);
    final MyElement foo = element.getFoo();
    assertEquals(foo, myMerger.mergeModels(MyElement.class, foo1, foo2));

    final int[] count = new int[]{0};
    element.accept(new DomElementVisitor() {
      @Override
      public void visitDomElement(DomElement _element) {
        count[0]++;
        assertEquals(_element, element);
      }
    });
    assertEquals(1, count[0]);

    count[0] = 0;
    final Set<DomElement> result = new HashSet<DomElement>();
    element.acceptChildren(new DomElementVisitor() {
      @Override
      public void visitDomElement(DomElement element) {
        count[0]++;
        result.add(element);
      }
    });
    assertEquals(new HashSet(Arrays.asList(foo, bar1, bar2, bar3)).toString().replace(",", "\n"), result.toString().replace(",", "\n"));
    assertEquals(new HashSet(Arrays.asList(foo, bar1, bar2, bar3)), result);
    assertEquals(4, count[0]);
  }

  public void testValidity() throws Throwable {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final MyElement element = createElement("", MyElement.class);
        final MyElement bar1 = element.addBar();
        final MyElement bar2 = element.addBar();
        final MyElement merged = myMerger.mergeModels(MyElement.class, bar1, bar2);
        assertTrue(merged.isValid());
        bar2.undefine();
        assertFalse(merged.isValid());
      }
    }.execute().throwException();
  }

  public interface MyElement extends DomElement {
    MyElement getFoo();
    List<MyElement> getBars();
    MyElement addBar();
  }

}
