/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ObjectNode;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class DisposerTest extends TestCase {

  protected MyDisposable myRoot;

  protected MyDisposable myFolder1;
  protected MyDisposable myFolder2;

  protected MyDisposable myLeaf1;
  private MyDisposable myLeaf2;

  private List myDisposedObjects = new ArrayList();

  private List myDisposeActions = new ArrayList();

  protected void setUp() throws Exception {
    myRoot = new MyDisposable("root");

    myFolder1 = new MyDisposable("folder1");
    myFolder2 = new MyDisposable("folder2");

    myLeaf1 = new MyDisposable("leaf1");
    myLeaf2 = new MyDisposable("leaf2");

    myDisposeActions.clear();
  }

  public void testDiposalAndAbsenceOfReferences() throws Exception {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myRoot, myFolder2);
    Disposer.register(myFolder1, myLeaf1);

    Disposer.dispose(myFolder1);
    assertFalse(myRoot.isDisposed());
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
    assertFalse(myFolder2.isDisposed());

    Disposer.dispose(myRoot);
    assertDisposed(myRoot);
    assertDisposed(myFolder2);

    Disposer.dispose(myLeaf1);
  }

  public void testDisposalOrder() throws Exception {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myRoot, myFolder2);

    Disposer.dispose(myRoot);

    List expected = new ArrayList();
    expected.add(myFolder2);
    expected.add(myLeaf1);
    expected.add(myFolder1);
    expected.add(myRoot);

    assertEquals(expected, myDisposedObjects);
  }

  public void testDirectCallOfDisposable() throws Exception {
    SelDisposable selfDisposable = new SelDisposable("root");
    Disposer.register(myRoot, selfDisposable);
    Disposer.register(selfDisposable, myFolder1);
    Disposer.register(myFolder1, myFolder2);

    selfDisposable.dispose();

    assertDisposed(selfDisposable);
    assertDisposed(myFolder1);
    assertDisposed(myFolder2);

    assertEquals(0, Disposer.getTree().getExecutedObjects().size());
  }

  public void testDisposeAndReplace() throws Exception {
    Disposer.register(myRoot, myFolder1);

    Disposer.disposeChildAndReplace(myFolder1, myFolder2);
    assertDisposed(myFolder1);

    Disposer.dispose(myRoot);
    assertDisposed(myRoot);
    assertDisposed(myFolder2);
  }

  public void testPostponedParentRegistration() throws Exception {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myLeaf1, myLeaf2);
    Disposer.register(myRoot, myFolder1);


    Disposer.dispose(myRoot);

    assertDisposed(myRoot);
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testDisposalOfParentless() throws Throwable {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder1, myFolder2);
    Disposer.register(myFolder2, myLeaf2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testDisposalOfParentess2() throws Throwable {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder2, myLeaf2);
    Disposer.register(myFolder1, myFolder2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testOverrideParentDisposable() throws Exception {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder2, myFolder1);
    Disposer.register(myRoot, myFolder1);

    Disposer.dispose(myFolder2);

    assertDisposed(myFolder2);
    assertFalse(myLeaf1.isDisposed());
    assertFalse(myFolder1.isDisposed());

    Disposer.dispose(myRoot);
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
  }

  public void testDisposableParentNotify() throws Exception {
    MyParentDisposable root = new MyParentDisposable("root");
    Disposer.register(root, myFolder1);

    MyParentDisposable sub = new MyParentDisposable("subFolder");
    Disposer.register(myFolder1, sub);

    Disposer.register(sub, myLeaf1);

    Disposer.dispose(root);


    ArrayList expected = new ArrayList();
    expected.add("beforeDispose: root");
    expected.add("beforeDispose: subFolder");
    expected.add("dispose: leaf1");
    expected.add("dispose: subFolder");
    expected.add("dispose: folder1");
    expected.add("dispose: root");


    assertEquals(toString(expected), toString(myDisposeActions));
  }


  private void assertDisposed(MyDisposable aDisposable) {
    assertTrue(aDisposable.isDisposed());
    assertFalse(aDisposable.toString(), Disposer.getTree().getObject2NodeMap().containsKey(aDisposable));

    Collection nodes = Disposer.getTree().getObject2NodeMap().values();
    for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
      assertNoReferenceKeptInTree((ObjectNode)iterator.next(), aDisposable);
    }

  }

  private void assertNoReferenceKeptInTree(ObjectNode aNode, MyDisposable aDisposable) {
    assertNotSame(aNode.getObject(), aDisposable);
    final Collection<ObjectNode> children = aNode.getChildren();
    if (children != null) {
      for (ObjectNode node:children) {
        assertNoReferenceKeptInTree(node, aDisposable);
      }
    }
  }

  private class MyDisposable implements Disposable {

    private boolean myDisposed = false;
    protected String myName;

    public MyDisposable(String aName) {
      myName = aName;
    }

    public void dispose() {
      myDisposed = true;
      myDisposedObjects.add(this);
      myDisposeActions.add("dispose: " + myName);
    }

    public boolean isDisposed() {
      return myDisposed;
    }

    public String toString() {
      return myName;
    }
  }

  private class MyParentDisposable extends MyDisposable implements Disposable.Parent {
    private MyParentDisposable(final String aName) {
      super(aName);
    }

    public void beforeTreeDispose() {
      myDisposeActions.add("beforeDispose: " + myName);
    }
  }

  private class SelDisposable extends MyDisposable {
    public SelDisposable(String aName) {
      super(aName);
    }

    public void dispose() {
      Disposer.dispose(this);
      super.dispose();
    }
  }

  private String toString(List list) {
    StringBuffer result = new StringBuffer();

    for (int i = 0; i < list.size(); i++) {
      String each = (String)list.get(i);
      result.append(each.toString());
      if (i < list.size() - 1) {
        result.append("\n");
      }
    }

    return result.toString();
  }
}
